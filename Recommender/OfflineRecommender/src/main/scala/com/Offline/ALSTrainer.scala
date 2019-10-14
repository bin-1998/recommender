package com.Offline

import breeze.numerics.sqrt
import org.apache.spark.SparkConf
import org.apache.spark.mllib.recommendation.{ALS, MatrixFactorizationModel, Rating}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

/**
 * @author hongxubin
 * @date 2019/10/7-下午10:56
 */
object ALSTrainer {

  val MONGODB_RATING_COLLECTION = "Rating"

  def main(args: Array[String]): Unit = {
    val config = Map(
      "spark.cores" -> "local[*]",
      "mongo.uri" -> "mongodb://localhost:27017/recommender",//url有数据库
      "mongo.db" -> "recommender"
    )
    val sparkConf=new SparkConf().setMaster(config("spark.cores")).setAppName("ALSTrainer")

    //创建一个SparkSession
    val sparkSession = SparkSession.builder().config(sparkConf).getOrCreate()

    import sparkSession.implicits._ //隐式参数

    implicit val mongoConfig=MongoConfig(config("mongo.uri"),config("mongo.db"))

    //加载评分数据
    val ratingRDD=sparkSession.read
      .option("uri",mongoConfig.uri)
      .option("collection",MONGODB_RATING_COLLECTION)
      .format("com.mongodb.spark.sql")
      .load()
      .as[MovieRating] //算做map
      .rdd
      .map(rating=>Rating(rating.uid,rating.mid,rating.score))  //转成rdd,并且去掉时间戳,没有错误可能是空格中文了
      .cache()

    //随机切分数据集,生成训练集和测试集
    val splits=ratingRDD.randomSplit(Array(0.8,0.2))
    val trainingRDD=splits(0)
    val testRDD=splits(1)

    //模型参数选择,输出最优参数
    adjustALSParam(trainingRDD,testRDD)

    sparkSession.close()

  }

  def adjustALSParam(trainData: RDD[Rating], testData: RDD[Rating]): Unit ={
    val result=for(rank<-Array(50,100,200);lambda<-Array(0.001,0.01,0.1))
      yield{
        val model=ALS.train(trainData,rank,50,lambda)
        val rmse=getRMSE(model,testData)
        (rank,lambda,rmse)
      }
    //控制台打印输出最优参数
    println(result.minBy(_._3))  //打印值最小的
  }

  def  getRMSE(model: MatrixFactorizationModel, data: RDD[Rating]):Double={
    //计算预测评分
    val userProducts=data.map(item=>(item.user,item.product))  //product是物品不是评分
    val predictRating=model.predict(userProducts)

    //以uid,mid作为外键,inner join实际观测值和预测值
    val observed=data.map(item=>((item.user,item.product),item.rating))
    val predict=predictRating.map(item=>((item.user,item.product),item.rating))

    //内连接得到(uid,mid),(actual,predict)
    sqrt(
      observed.join(predict).map{
        case ((uid,mid),(actual,pre))=>
          val err=actual-pre
          err*err
      }.mean()   //计算平均值
    )

  }
}

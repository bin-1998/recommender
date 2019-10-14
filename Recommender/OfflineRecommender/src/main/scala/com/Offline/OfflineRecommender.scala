package com.Offline

import org.apache.spark.SparkConf
import org.apache.spark.mllib.recommendation.{ALS, Rating}
import org.apache.spark.sql.SparkSession
import org.jblas.DoubleMatrix

/**
 * @author hongxubin
 * @date 2019/10/7-下午11:34
 */
case class MovieRating(uid:Int,mid:Int,score:Double,timestamp:Int)

case class MongoConfig(uri:String,db:String)

//定义一个基准推荐对象
case class Recommendation(mid:Int,score:Double)

//定义基于预测评分的用户推荐列表
case class UserRecs(uid:Int,recs:Seq[Recommendation])

//定义基于LFM电影特征向量的电影相似度列表 //隐语义模型
case class MovieRecs(mid:Int,recs:Seq[Recommendation])

object OfflineRecommender {

  //定义表名和向量
  val MONGODB_RATING_COLLECTION = "Rating"
  val USER_RECS = "UserRecs"
  val MOVIE_RECS = "MovieRecs"

  val USER_MAX_RECOMMENDATION = 20

  def main(args: Array[String]): Unit = {


    val config = Map(
      "spark.cores" -> "local[*]",
      "mongo.uri" -> "mongodb://localhost:27017/recommender",
      "mongo.db" -> "recommender"
    )

    val sparkConf = new SparkConf().setMaster(config("spark.cores")).setAppName("OfflineRecommender")

    //创建一个SparkSession
    val sparkSession = SparkSession.builder().config(sparkConf).getOrCreate()

    import sparkSession.implicits._

    implicit val mongoConfig = MongoConfig(config("mongo.uri"), config("mongo.db"))

    //加载数据
    val ratingRDD = sparkSession.read
      .option("uri", mongoConfig.uri)
      .option("collection", MONGODB_RATING_COLLECTION)
      .format("com.mongodb.spark.sql")
      .load()
      .as[MovieRating] //不用rdd也能变成DF
      .rdd
      .map(rating => (rating.uid, rating.mid, rating.score)) //转化成rdd并且去掉时间戳
      .cache()

    //从rating数据中提取所有的uid和mid,并去重
    val userRDD = ratingRDD.map(_._1).distinct()
    val movieRDD = ratingRDD.map(_._2).distinct()

    //训练隐语义模型
    val trainData = ratingRDD.map(x => Rating(x._1, x._2, x._3))

    val (rank, iterations, lambda) = (50, 5, 0.01)
    val model = ALS.train(trainData, rank, iterations, lambda) //训练集,隐藏特征数,交叉最小二乘法的迭代次数,正则化系数

    //基于用户和电影的隐特征,计算预测评分,得到用户的推荐列表
    //a.计算user和movie的笛卡尔积,得到一个空矩阵
    val useMovies = userRDD.cartesian(movieRDD)

    //b.调用model的predict(usermovies)
    val preRatings = model.predict(useMovies) //预测

    val userRecs = preRatings
      .filter(_.rating > 0) //过滤出评分大于0的项
      .map(rating => (rating.user, (rating.product, rating.rating)))
      .groupByKey()
      .map {
        case (uid, recs) => UserRecs(uid, recs.toList.sortWith(_._2 > _._2).take(USER_MAX_RECOMMENDATION).map(x => Recommendation(x._1, x._2)))
      }
      .toDF()

    userRecs.write
      .option("uri", mongoConfig.uri)
      .option("collection", USER_RECS)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    //基于电影隐特征,计算相似度矩阵,得到电影的相似度列表 评分和隐藏特征都算　物品的特征
    val movieFeatures = model.productFeatures //电影隐特征
      .map {
        case (mid, features) => (mid, new DoubleMatrix(features))   //双矩阵
      }
    //对所有电影两两计算它们的相识度,先做笛卡尔积
    val movieRecs = movieFeatures.cartesian(movieFeatures)
      .filter {
        //把自己跟自己配对的过滤掉
        case (a, b) => a._1 != b._1
      }
      .map {
        //求得相似度　并转化格式
        case (a, b) => {
          val simScore = this.consinSim(a._2, b._2)
          (a._1, (b._1, simScore))
        }
      }
      .filter(_._2._2 > 0.6)
      .groupByKey()
      .map {
        case (mid, items) => MovieRecs(mid, items.toList.sortWith(_._2 > _._2).map(x => Recommendation(x._1, x._2)))
      }
      .toDF()

    movieRecs.write
      .option("uri",mongoConfig.uri)
      .option("collection",MOVIE_RECS)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()
    sparkSession.stop()
  }

  def consinSim(movie1: DoubleMatrix, movie2: DoubleMatrix): Double ={
    movie1.dot(movie2)/movie1.norm2()*movie2.norm2()   //余弦相似度

  }

}

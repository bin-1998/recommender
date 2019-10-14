package com.statistics

import java.text.SimpleDateFormat
import java.util.Date

import org.apache.spark.SparkConf
import org.apache.spark.sql.{DataFrame, SparkSession}

/**
 * @author hongxubin
 * @date 2019/10/8-上午10:54
 */
case class Movie(mid:Int,name:String,descri:String,timelong:String,issue:String,shoot:String,language:String,genres:String,actors:String,directors:String)

case class Rating(uid:Int,mid:Int,score:Double,timestamp:Int)

case class MongoConfig(uri:String,db:String)

//定义一个基准推荐对象
case class Recommendation(mid:Int,score:Double)

//定义电影类别top10推荐对象
case class GenresRecommendation(genres:String,recs:Seq[Recommendation])

object StatisticsRecommender {

  //定义表名
  val MONGODB_MOVIE_COLLECTION="Movie"
  val MONGODB_RATING_COLLECTION="Rating"

  // 统计的表的名称
  val RATE_MORE_MOVIES = "RateMoreMovies"   //热度最高  ，评分最多的电影
  val RATE_MORE_RECENTLY_MOVIES = "RateMoreRecentlyMovies"  //最近热度最高的电影
  val AVERAGE_MOVIES = "AverageMovies"        //电影的平均评分
  val GENRES_TOP_MOVIES = "GenresTopMovies"   //各类型的top10

  def main(args: Array[String]): Unit = {
    val config=Map(
      "spark.cores"->"local[*]",
      "mongo.uri"->"mongodb://localhost:27017/recommender",
      "mongo.db" -> "recommender"
    )
    //创建sparkconf
    val sparkConf=new SparkConf().setMaster(config("spark.cores")).setAppName("StatisticsRecommender")

    //创建SparkSession
    val sparkSession=SparkSession.builder().config(sparkConf).getOrCreate()

    //隐式参数
    import sparkSession.implicits._ // 这里的spark是会话窗口
    implicit val mongoConfig=MongoConfig(config("mongo.uri"),config("mongo.db"))

    //从mongdb里加载数据
    val movieDF=sparkSession.read
      .option("uri",mongoConfig.uri)
      .option("collection",MONGODB_MOVIE_COLLECTION)
      .format("com.mongodb.spark.sql")
      .load()
      .as[Movie]//dataset
      .toDF()

    val ratingDF=sparkSession.read
      .option("uri",mongoConfig.uri)
      .option("collection",MONGODB_RATING_COLLECTION)
      .format("com.mongodb.spark.sql")
      .load()
      .as[Rating]//dataset
      .toDF()

    //创建名为ratings的临时表,用来缓存到内存中，以方便调用
    ratingDF.createOrReplaceTempView("ratings")

    //TODO:不同的统计推荐结果
    //1.历史热门统计,历史评分数据最多
    val rateMoreMoviesDF=sparkSession.sql("select mid,count(mid) as count from ratings group by mid")
    //把结果写入对应的mongo表中
    storeDFInMongoDB(rateMoreMoviesDF,RATE_MORE_MOVIES)

    //2.近期热门统计,按照"yyyyMM"格式选取最近的评分数据,统计评分个数
    //创建一个日期格式化工具
    val simpleDateFormat=new SimpleDateFormat("yyyyMM")
    //注册udf,把时间戳转换成年月格式
    sparkSession.udf.register("changeDate",(x:Int)=>simpleDateFormat.format(new Date(x*1000L)).toInt)
    //对原始数据做预处理,去掉uid
    val ratingOfYearMonth=sparkSession.sql("select mid,score,changeDate(timestamp) as yearmonth from ratings")
    //生成临时表
    ratingOfYearMonth.createOrReplaceTempView("ratingOfMonth")

    //从ratingOfMonth里查找电影在各月份的评分,mid,count,yearmonth
    val rateMoreRecentlyMoviesDF=sparkSession.sql("select mid,count(mid) as count from ratingOfMonth group by yearmonth,mid order by yearmonth desc,count desc")
    //存入mongodb
    storeDFInMongoDB(rateMoreRecentlyMoviesDF,RATE_MORE_RECENTLY_MOVIES)

    //3.优质电影统计,统计电影的平均评分
    val averageMoviesDF=sparkSession.sql("select mid,avg(score) as avg from ratings group by mid")
    storeDFInMongoDB(averageMoviesDF,AVERAGE_MOVIES)
    //4.各类别TOP电影统计
    //a.定义所有类别
//    val genres =
//    List("Action","Adventure","Animation","Comedy","Crime","Documentary","Drama","Family",
//      "Fantasy","Foreign","History","Horror","Music","Mystery","Romance","Science","Tv","Thriller","War","Western")
//    //b.把平均评分加入movie表,加一列,inner join
//    val  movieWithScore=movieDF.join(averageMoviesDF,"mid")
//    //c.为做笛卡尔积,把genres转成rdd
//    val genresRDD=sparkSession.sparkContext.makeRDD(genres)
//    //d.计算TOP10,首先对类别和电影做笛卡尔积
//    val genresTopMoviesDF=genresRDD.cartesian(movieWithScore.rdd)
//      .filter{//case替代map(genresTopMoviesDF=>{})的写法 不在使用._1 ._2  上一个父rdd的类型值可直接命名为变量使用
//        //找出movie字段的genres值包含当前类别
//        case (genre,movieRow)=>movieRow.getAs[String]("genres").toLowerCase().contains(genre.toLowerCase())
//      }
//      .map{
//        case (genre,movieRow)=>(genre,(movieRow.getAs[Int]("mid"),movieRow.getAs[Double]("avg")))
//      }
//      .groupByKey()
//      .map{
//        case (genre,items)=>GenresRecommendation(genre,items.toList.sortWith(_._2>_._2).take(10).map (
//          items => Recommendation(items._1, items._2)
//        ))
//      }
//      .toDF()
//
//    storeDFInMongoDB(genresTopMoviesDF,GENRES_TOP_MOVIES)




    sparkSession.stop()



  }
  def storeDFInMongoDB(df:DataFrame,collection_name: String)(implicit mongoConfig: MongoConfig): Unit ={
    df.write
      .option("uri",mongoConfig.uri)
      .option("collection",collection_name)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()
  }


}

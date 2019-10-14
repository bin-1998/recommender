package com

import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.{MongoClient, MongoClientURI}
import org.apache.spark
import org.apache.spark.SparkConf
import org.apache.spark.sql.{DataFrame, SparkSession}

/**
 * @author hongxubin
 * @date 2019/10/6-下午5:57
 */
/**
 * 260                                            电影ID,mid
 * Star Wars: Episode IV - A New Hope (1977)      影名称,name
 * Princess Leia                                  详情描述,descri
 * 121 minutes                                    电影时长,timelong
 * September 21, 2004                             发行时间,issue
 * 1977                                           拍摄时间,shoot
 * English                                        语言,language
 * Action|Adventure|Sci-Fi                        类型,genres
 * Mark Hamill|Harr                               演员表,actors
 * George Lucas                                    导演,directors
 **/

case class Movie(mid:Int,name:String,descri:String,timelong:String,issue:String,shoot:String,language:String,genres:String,actors:String,directors:String)

/**
 * 1            用户id
 * 31           电影id
 * 2.5          评分
 * 1260759144   时间戳
 */
case class Rating(uid:Int,mid:Int,score:Double,timestamp:Int)

/**
* 1            用户id
* 31           电影id
* tag          标签
* 1260759144   时间戳
*/
case class Tag(uid:Int,mid:Int,tag:String,timestamp:Int)

//把mongo和es的配置封装成样例类
/**
 *
 * @param uri MongoDB连接
 * @param db  MongoDB数据库
 */
case class MongoConfig(uri:String,db:String)

/**
 *
 * @param httpHosts         http主机列表,逗号分隔
 * @param transportHosts    transport,主机列表
 * @param index             需要操作的索引
 * @param clustername           集群名称,默认elasticsearch
 */
case class ESConfig(httpHosts:String,transportHosts:String,index:String,clustername:String)

object recommender {
  val MOVIE_DATA_PATH="/home/hadoop/IdeaProjects/MovieRecommenderSystemx/Recommender/DataLoader/src/main/resources/movies.csv"
  val RATING_DATA_PATH="/home/hadoop/IdeaProjects/MovieRecommenderSystemx/Recommender/DataLoader/src/main/resources/ratings.csv"
  val TAG_DATA_PATH="/home/hadoop/IdeaProjects/MovieRecommenderSystemx/Recommender/DataLoader/src/main/resources/tags.csv"
  val MONGODB_MOVIE_COLLECTION="Movie"
  val MONGODB_RATING_COLLECTION="Rating"
  val MONGODB_TAG_COLLECTION="Tag"
  val ES_MOVIE_INDEX="Movie"

  def main(args: Array[String]): Unit = {
    val config=Map(
      "spark.cores"->"local[*]",
      "mongo.uri"->"mongodb://localhost:27017/recommender",
      "mongo.db" -> "recommender",
      "es.httpHosts"->"localhost:9200",
      "es.transportHosts"->"localhost:9300",
      "es.index"->"recommender",
      "es.cluster.name"->"elasticsearch"
    )
    val sparkConf=new SparkConf().setMaster(config("spark.cores")).setAppName("recommender")
    val sparkSession=new SparkSession.Builder().config(sparkConf).getOrCreate() //Session有两个，一个是spark一个是spark。sql的
    import sparkSession.implicits._
    val movieRDD=sparkSession.sparkContext.textFile(MOVIE_DATA_PATH)
    //清洗电影数据
    val movieDF=movieRDD.map(
      item=>{
        val attr=item.split("\\^")
        Movie(attr(0).toInt,attr(1).trim,attr(2).trim,attr(3).trim,attr(4).trim,attr(5).trim,attr(6).trim,attr(7).trim,attr(8).trim,attr(9).trim)
      }
    ).toDF()
    //清洗评分数据
    val ratingRDD=sparkSession.sparkContext.textFile(RATING_DATA_PATH)
    val ratingDF=ratingRDD.map(// 添加一个函数  a=>{} a代表一行 或者直接 （_.1,_.2）
      item=>{
      val attr=item.split("\\^")
        Rating(attr(0).toInt,attr(1).toInt,attr(2).toDouble,attr(3).toInt)
      }
    ).toDF()  //只要每一行都是case class 就可以toDF
    val tagRDD=sparkSession.sparkContext.textFile(TAG_DATA_PATH)

    val tagDF=tagRDD.map(
      item=>{
        val attr=item.split(",")
        Tag(attr(0).toInt,attr(1).toInt,attr(2).trim,attr(3).toInt)
      }
    ).toDF()

    //数据预处理
    //将数据保存到MongoDB
    implicit val mongoConfig=MongoConfig(config("mongo.uri"),config("mongo.db")) //隐式参数

    storeDataInMongoDB(movieDF,ratingDF,tagDF)

    //数据预处理,把movie对应的tag信息添加进去, 加一列tag1|tag2|tag3....agg() sql里group后面的where
    import org.apache.spark.sql.functions._

    val newTag=tagDF.groupBy($"mid")
      .agg(concat_ws("|",collect_set("tag")).as("tags"))
      .select("mid","tags")

    //newtag和movie做join,数据合并在一起 Seq() 把一组数包装成表中的行
    val movieWithTagsDF=movieDF.join(newTag,Seq("mid"),"left")

    //implicit val esConfig=ESConfig(config("es.httpHosts"),config("es.transportHosts"),config("es.index"),config("es.cluster.name"))

    //保存到ES
    //storeDataInES(movieWithTagsDF)
    sparkSession.stop()
  }
  def storeDataInMongoDB(movieDF:DataFrame,ratingDF:DataFrame,tagDF:DataFrame)(implicit mongoConfig: MongoConfig): Unit ={
    //新建一个mongodb的一个连接
    val mongoClient=MongoClient(MongoClientURI(mongoConfig.uri))

    //如果mongodb中已经有相应的数据库,先删除
    mongoClient(mongoConfig.db)(MONGODB_MOVIE_COLLECTION).dropCollection()
    mongoClient(mongoConfig.db)(MONGODB_RATING_COLLECTION).dropCollection()
    mongoClient(mongoConfig.db)(MONGODB_TAG_COLLECTION).dropCollection()

    //将DF数据写入对应的mongodb表中
    movieDF.write
      .option("uri",mongoConfig.uri)
      .option("collection",MONGODB_MOVIE_COLLECTION)
      .mode("overwrite")
      .format("com.mongodb.spark.sql") //选择什么那种数据库
      .save()
    ratingDF.write
      .option("uri",mongoConfig.uri)
      .option("collection",MONGODB_RATING_COLLECTION)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()
    tagDF.write
      .option("uri",mongoConfig.uri)
      .option("collection",MONGODB_TAG_COLLECTION)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    // 对数据表建索引
    mongoClient(mongoConfig.db)(MONGODB_MOVIE_COLLECTION).createIndex(MongoDBObject("mid" -> 1))
    mongoClient(mongoConfig.db)(MONGODB_RATING_COLLECTION).createIndex(MongoDBObject("uid" -> 1))
    mongoClient(mongoConfig.db)(MONGODB_RATING_COLLECTION).createIndex(MongoDBObject("mid" -> 1))
    mongoClient(mongoConfig.db)(MONGODB_TAG_COLLECTION).createIndex(MongoDBObject("uid" -> 1))
    mongoClient(mongoConfig.db)(MONGODB_TAG_COLLECTION).createIndex(MongoDBObject("mid" -> 1))
    // 关闭 MongoDB 的连接
    mongoClient.close()

  }
//  def storeDataInES(movieDF:DataFrame)(implicit eSConfig: ESConfig): Unit ={
//    //新建es配置
//    val settings:Settings=Settings.builder().put("cluster.name",eSConfig.clustername).build()
//
//    //新建es客户端
//    val esClient=new PreBuiltTransportClient(settings)
//
//    val REGEX_HOST_PORT="(.+):(\\d+)".r
//    eSConfig.transportHosts.split(",").foreach{
//      case REGEX_HOST_PORT(host:String,port:String)=>{
//        esClient.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(host),port.toInt))
//      }
//    }
//
//    //清理遗留数据
//    if(esClient.admin().indices().exists(new IndicesExistsRequest(eSConfig.index)).actionGet().isExists())
//    {
//      esClient.admin().indices().delete(new DeleteIndexRequest(eSConfig.index))
//    }
//
//    esClient.admin().indices().create(new CreateIndexRequest(eSConfig.index))
//
//    movieDF.write
//      .option("es.nodes",eSConfig.httpHosts)
//      .option("es.http.timeout","100m")
//      .option("es.mapping.id","mid")
//      .mode("overwrite")
//      .format("org.elasticsearch.spark.sql")
//      .save(eSConfig.index+"/"+ES_MOVIE_INDEX)
//
//  }

}


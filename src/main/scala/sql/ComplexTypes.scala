package sql

import java.sql.Date

import org.apache.spark.sql.SQLContext
import org.apache.spark.{SparkConf, SparkContext}

object ComplexTypes {

  case class Cust(id: Integer, name: String, trans: Seq[Transaction],
    billing:Address, shipping:Address)

  case class Transaction(id: Integer, date: Date, amount: Double)

  case class Address(state:String)

  def main(args: Array[String]) {
    val conf = new SparkConf().setAppName("SQL-ComplexTypes").setMaster("local[4]")
    val sc = new SparkContext(conf)
    val sqlContext = new SQLContext(sc)

    import sqlContext.implicits._

    // create a sequence of case class objects
    // (we defined the case class above)
    val custs = Seq(
      Cust(1, "Widget Co",
        Seq(Transaction(1, Date.valueOf("2012-01-01"), 100.0)),
        Address("AZ"), Address("CA")),
      Cust(2, "Acme Widgets",
        Seq(Transaction(2, Date.valueOf("2014-06-15"), 200.0),
            Transaction(3, Date.valueOf("2014-07-01"), 50.0)),
        Address("CA"), null),
      Cust(3, "Widgetry",
        Seq(Transaction(4, Date.valueOf("2014-01-01"), 150.0)),
        Address("CA"), Address("CA"))
      // Cust(4, "Widgets R Us", 410500.00, 0.0, "CA"),
      // Cust(5, "Ye Olde Widgete", 500.00, 0.0, "MA")
    )
    // make it an RDD and convert to a DataFrame
    val customerDF = sc.parallelize(custs, 4).toDF()


    println("*** inferred schema takes nesting and arrays into account")
    customerDF.printSchema()

    customerDF.registerTempTable("customer")

    println("*** Query results reflect complex structure")
    val allCust = sqlContext.sql("SELECT * FROM customer")
    allCust.show()

    //
    // notice how the following query refers to shipping.date and
    // trans[1].date but some records don't have these components: Spark SQL
    // handles these cases quite gracefully
    //

    println("*** Projecting from deep structure doesn't blow up when it's missing")
    val projectedCust =
      sqlContext.sql("SELECT id, name, shipping.state, trans[1].date AS secondTrans FROM customer")
    projectedCust.show()
    projectedCust.printSchema()

    //
    // The 'trans' field is an array of Transaction structures, but you can pull just the
    // dates out of each one by referencing trans.date
    //

    println("*** Reach into each element of an array of structures by omitting the subscript")
    val arrayOfStruct =
      sqlContext.sql("SELECT id, trans.date AS transDates FROM customer")
    arrayOfStruct.show()
    arrayOfStruct.printSchema()

    println("*** Group by a nested field")
    val groupByNested =
      sqlContext.sql("SELECT shipping.state, count(*) AS customers FROM customer GROUP BY shipping.state")
    groupByNested.show()

    println("*** Order by a nested field")
    val orderByNested =
      sqlContext.sql("SELECT id, shipping.state FROM customer ORDER BY shipping.state")
    orderByNested.show()


  }
}
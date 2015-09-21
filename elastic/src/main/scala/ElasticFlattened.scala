package funnel
package elastic

/**
 * This class provides a consumer for the funnel montioring stream that takes
 * the events emitted and constructs a JSON document that will be sent to elastic
 * search. Each and every metric key has its own individual document, where fields
 * are flattened into a simplistic structure, which is optiomal for the manner in
 * which elastic search stores documents and manages in-memory indexing with Lucene.
 *
 *
 */
case class ElasticFlattened(M: Monitoring){
  import Elastic._

  // def publish(flaskName: String, flaskCluster: String): ES[Unit] = {

  // }
}
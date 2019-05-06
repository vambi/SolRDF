package org.gazzax.labs.solrdf.search.qparser;

import org.apache.lucene.search.Query;

/**
 * A simple Lucene wrapper for a Jena Query.
 * 
 * @author Andrea Gazzarini
 * @since 1.0
 */
public class SparqlQuery extends Query {
	final com.hp.hpl.jena.query.Query query;
	
	final boolean hybrid;
	
	/**
	 * Builds a new query with the given data.
	 * 
	 * @param query the wrapped query.
	 * @param hybrid a simple flag indicating if we have to switch in hybrid mode (i.e. SPARQL query with Solr params). 
	 */
	public SparqlQuery(final com.hp.hpl.jena.query.Query query, final boolean hybrid) {
		this.query = query;
		this.hybrid = hybrid;
	}
	
	/**
	 * Returns the wrapped Jena {@link com.hp.hpl.jena.query.Query}.
	 * 
	 * @return the wrapped Jena {@link com.hp.hpl.jena.query.Query}.
	 */
	public com.hp.hpl.jena.query.Query getQuery() {
		return query;
	}
	
	/**
	 * Returns true if this query contains both a SPARQL query and other Solr parameters.
	 * 
	 * @return true if this query contains both a SPARQL query and other Solr parameters.
	 */
	public boolean isHybrid() {
		return hybrid;
	}
	
	@Override
	public String toString(final String field) {
		return query.toString();
	}

	@Override
	public boolean equals(Object other) {
		return sameClassAs(other)&& query.equals(other);
	}

	@Override
	public int hashCode() {
	 return classHash() ;
	}
}
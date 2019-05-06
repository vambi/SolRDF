package org.gazzax.labs.solrdf.graph.standalone;

import static org.gazzax.labs.solrdf.F.fq;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.Term;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.QParser;
import org.gazzax.labs.solrdf.Field;
import org.gazzax.labs.solrdf.log.Log;
import org.gazzax.labs.solrdf.log.MessageCatalog;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.slf4j.LoggerFactory;

import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.datatypes.xsd.XSDDateTime;

/**
 * A registry for datatyped literal objects mappings.
 * 
 * @author Andrea Gazzarini
 * @since 1.0
 */
class FieldInjectorRegistry {
	static final Log LOGGER = new Log(LoggerFactory.getLogger(LocalGraph.class));
	
	private static ThreadLocal<DateTimeFormatter> isoFormatterCache = new ThreadLocal<DateTimeFormatter>() {
		@Override
		protected DateTimeFormatter initialValue() {
			return new DateTimeFormatterBuilder()
				.append(DateTimeFormat.forPattern("yyyy-MM-dd"))                                            
				.appendOptional(
						new DateTimeFormatterBuilder()
							.appendLiteral('T')
							.appendOptional(
									new DateTimeFormatterBuilder()
										.append(DateTimeFormat.forPattern("HH"))
										.appendOptional(
												new DateTimeFormatterBuilder()
													.append(DateTimeFormat.forPattern(":mm"))
														.appendOptional(
																new DateTimeFormatterBuilder()
																	.append(DateTimeFormat.forPattern(":ss"))
																.toParser())
												.toParser())
									.toParser())
						.toParser())
				.toFormatter();
		}
	};
	
	/**
	 * Command interface.
	 * 
	 * @author Andrea Gazzarini
	 * @since 1.0
	 */
	interface FieldInjector {
		/**
		 * Injects a given value into a document.
		 * 
		 * @param triple the {@link SolrInputDocument} representing a triple.
		 * @param value the value of the object member.
		 */
		void inject(SolrInputDocument triple, Object value);

		/**
		 * Adds to the given list a new constraint query with the given data.
		 * 
		 * @param filters the filter list.
		 * @param value the new constraint value. 
		 */
		void addFilterConstraint(List<Query> filters, String value, SolrQueryRequest request);
		
		/**
		 * Appends to a given {@link StringBuilder}, and additional AND clause with the given value.
		 * 
		 * @param builder the query builder.
		 * @param value the value of the additional constraint.
		 */
		void addConstraint(StringBuilder builder, String value);
	}
	
	final FieldInjector booleanFieldInjector = new FieldInjector() {
		@Override
		public void inject(final SolrInputDocument document, final Object value) {
			document.setField(Field.BOOLEAN_OBJECT, value);
		}

		@Override
		public void addFilterConstraint(final List<Query> filters, final String value, SolrQueryRequest request) {
			filters.add(new TermQuery(new Term(Field.BOOLEAN_OBJECT, value.substring(0,1).toUpperCase())));
		}
		
		@Override
		public void addConstraint(final StringBuilder builder, final String value) {
			builder.append(Field.BOOLEAN_OBJECT).append(":").append(value);
		}		
	};

	final FieldInjector numericFieldInjector = new FieldInjector() {
		@Override
		public void inject(final SolrInputDocument document, final Object value) {
			document.setField(Field.NUMERIC_OBJECT, value instanceof BigDecimal ? ((BigDecimal)value).doubleValue() : value);
		}
		
		@Override
		public void addFilterConstraint(final List<Query> filters, final String value, SolrQueryRequest request) {
			final Double number = Double.valueOf(value);
			filters.add(DoublePoint.newRangeQuery(Field.NUMERIC_OBJECT, number, number));
		}		

		@Override
		public void addConstraint(final StringBuilder builder, final String value) {
			builder.append(Field.NUMERIC_OBJECT).append(":").append(value);
		}		
	};
	
	final FieldInjector dateTimeFieldInjector = new FieldInjector() {
		@Override
		public void inject(final SolrInputDocument document, final Object value) {
			final Calendar calendar = ((XSDDateTime)value).asCalendar();
			final long millis = calendar.getTimeInMillis();
			document.setField(
					Field.DATE_OBJECT, 
					new Date(millis + calendar.getTimeZone().getOffset(millis)));
		}
		
		@Override
		public void addFilterConstraint(final List<Query> filters, final String value, SolrQueryRequest request) {
			try {
				filters.add(QParser.getParser(
								new StringBuilder()
									.append(Field.DATE_OBJECT)
									.append(": \"")
									.append(format(value))
									.append("\"")
									.toString(), "lucene", request).getQuery());
			} catch (final Exception exception) {
				LOGGER.error(MessageCatalog._00110_INVALID_DATE_VALUE, exception, value);
				throw new IllegalArgumentException(exception);
			}
		}				

		@Override
		public void addConstraint(final StringBuilder builder, final String value) {
			try {
				builder
					.append(Field.DATE_OBJECT)
					.append(": \"")
					.append(format(value))
					.append("\"");
			} catch (final IllegalArgumentException exception) {
				LOGGER.error(MessageCatalog._00110_INVALID_DATE_VALUE, exception, value);
				throw new IllegalArgumentException(exception);
			}
			
		}	
		
		protected String format(final String value) {
			if (value.endsWith("Z")) {
				return value;
			}
			return new StringBuilder()
					.append(
							isoFormatterCache.get()
								.parseLocalDateTime(value)
								.toString("yyyy-MM-dd'T'HH:mm:ss")+"Z")
					.toString();
		}
	};
	
	final FieldInjector catchAllFieldInjector = new FieldInjector() {
		@Override
		public void inject(final SolrInputDocument document, final Object value) {
			document.setField(Field.TEXT_OBJECT, String.valueOf(value));
		}

		@Override
		public void addFilterConstraint(final List<Query> filters, final String value, SolrQueryRequest request) {
			PhraseQuery.Builder builder = new PhraseQuery.Builder();
			builder.add(new Term(Field.TEXT_OBJECT, value));
			final PhraseQuery query = builder.build();
			filters.add(query);
		}		
		
		@Override
		public void addConstraint(final StringBuilder builder, final String value) {
			builder.append(fq(Field.TEXT_OBJECT, value));
		}				
	};
	
	final Map<String, FieldInjector> injectors = new HashMap<String, FieldInjector>();
	{
		injectors.put(XSDDatatype.XSDboolean.getURI(), booleanFieldInjector);		
		
		injectors.put(XSDDatatype.XSDint.getURI(), numericFieldInjector);
		injectors.put(XSDDatatype.XSDinteger.getURI(), numericFieldInjector);
		injectors.put(XSDDatatype.XSDdecimal.getURI(), numericFieldInjector);		
		injectors.put(XSDDatatype.XSDdouble.getURI(), numericFieldInjector);		
		injectors.put(XSDDatatype.XSDlong.getURI(), numericFieldInjector);
		
		injectors.put(XSDDatatype.XSDdate.getURI(), dateTimeFieldInjector);
		injectors.put(XSDDatatype.XSDdateTime.getURI(), dateTimeFieldInjector);	
		
		injectors.put(null, catchAllFieldInjector);	
	}
	
	/**
	 * Returns the {@link FieldInjector} that is in charge to handle the given (datatype) URI.
	 * 
	 * @param uri the datatype URI.
	 * @return the {@link FieldInjector} that is in charge to handle the given (datatype) URI.
	 */
	public FieldInjector get(final String uri) {
		final FieldInjector injector = injectors.get(uri);
		return injector != null ? injector : catchAllFieldInjector;
	}
	
	/**
	 * Returns the {@link FieldInjector} used for storing plain strings.
	 * 
	 * @return the {@link FieldInjector} used for storing plain strings.
	 */
	public FieldInjector catchAllInjector() {
		return catchAllFieldInjector;
	}
}
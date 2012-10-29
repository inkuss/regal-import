import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openrdf.query.QueryLanguage;

import de.nrw.hbz.edoweb2.sesame.SesameFacade;

public class SesameFacadeTest
{
	@Before
	public void setUp()
	{

	}

	@Test
	public void testSparql()
	{
		SesameFacade facade = new SesameFacade("fedoraAdmin", "fedoraAdmin1",
				"/tmp/myRepository");
		facade.findTriples("SELECT ?s ?p ?o WHERE{ ?s ?p ?o }",
				QueryLanguage.SPARQL, null);
	}

	@Test
	public void testSerql()
	{
		SesameFacade facade = new SesameFacade("fedoraAdmin", "fedoraAdmin1",
				"/tmp/myRepository");
		String queryString = "SELECT x, y FROM {x} p {y}";
		facade.findTriples(queryString, QueryLanguage.SERQL, null);
	}

	@After
	public void tearDown()
	{

	}
}

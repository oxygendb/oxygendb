package com.orientechnologies.orient.test.server.network.http;

import com.orientechnologies.core.record.impl.YTEntityImpl;
import java.util.List;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test HTTP "function" command.
 */
public class HttpFunctionTest extends BaseHttpDatabaseTest {

  @Test
  public void callFunction() throws Exception {
    // CREATE FUNCTION FIRST
    ClassicHttpResponse response1 =
        post("command/" + getDatabaseName() + "/sql/")
            .payload(
                "CREATE FUNCTION hello \"return 'Hello ' + name + ' ' + surname;\" PARAMETERS"
                    + " [name,surname] LANGUAGE javascript",
                CONTENT.TEXT)
            .getResponse();
    Assert.assertEquals(response1.getReasonPhrase(), response1.getCode(), 200);

    ClassicHttpResponse response2 =
        post("function/" + getDatabaseName() + "/hello")
            .payload("{\"name\": \"Jay\", \"surname\": \"Miner\"}", CONTENT.TEXT)
            .setUserName("admin")
            .setUserPassword("admin")
            .getResponse();
    Assert.assertEquals(response2.getReasonPhrase(), response2.getCode(), 200);

    String response = EntityUtils.toString(getResponse().getEntity());

    Assert.assertNotNull(response);

    var responseDoc = new YTEntityImpl();
    responseDoc.fromJSON(response);
    YTEntityImpl result =
        ((List<YTEntityImpl>) responseDoc.field("result")).get(0);

    Assert.assertEquals(result.field("value"), "Hello Jay Miner");
  }

  @Override
  public String getDatabaseName() {
    return "httpfunction";
  }
}

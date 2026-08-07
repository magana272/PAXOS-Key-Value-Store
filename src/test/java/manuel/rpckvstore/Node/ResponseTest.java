package manuel.rpckvstore.Node;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ResponseTest {

    @Test
    void fieldsAndToStringReflectBody() {
        Response r = new Response("header", "body");

        assertEquals("header", r.header);
        assertEquals("body", r.body);
        assertEquals("body", r.toString());
    }

    @Test
    void toStringReturnsNullBody() {
        assertNull(new Response("h", null).toString());
    }
}
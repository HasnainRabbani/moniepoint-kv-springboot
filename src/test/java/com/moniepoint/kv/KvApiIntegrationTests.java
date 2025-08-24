package com.moniepoint.kv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class KvApiIntegrationTests {

	@Autowired
	MockMvc mvc;

	@Test
	@DisplayName("Health endpoint returns OK")
	void health() throws Exception {
		mvc.perform(get("/kv/v1/health")).andExpect(status().isOk()).andExpect(content().string("OK"));
	}

	@Test
	@DisplayName("PUT -> GET -> DELETE -> 404")
	void putGetDelete() throws Exception {
		// PUT
		mvc.perform(put("/kv/v1/hello").contentType(MediaType.TEXT_PLAIN).content("world".getBytes()))
				.andExpect(status().isNoContent());

		// GET value
		mvc.perform(get("/kv/v1/hello")).andExpect(status().isOk()).andExpect(content().bytes("world".getBytes()));

		// DELETE
		mvc.perform(delete("/kv/v1/hello")).andExpect(status().isNoContent());

		// GET 404
		mvc.perform(get("/kv/v1/hello")).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("Batch insert (JSON) and range query")
	void batchAndRangeJson() throws Exception {
		String jsonBody = """
				[
				  {"key":"k1","value":"v1"},
				  {"key":"k2","value":"v2"},
				  {"key":"k3","value":"v3"}
				]
				""";

		// Batch insert JSON
		mvc.perform(post("/kv/v1/batch").contentType(MediaType.APPLICATION_JSON).content(jsonBody))
				.andExpect(status().isNoContent());

		// Range query
		mvc.perform(get("/kv/v1/range").param("start", "k1").param("end", "k9")).andExpect(status().isOk())
				.andExpect(content().string(containsString("k1=v1")))
				.andExpect(content().string(containsString("k2=v2")))
				.andExpect(content().string(containsString("k3=v3")));
	}

	@Test
	@DisplayName("Empty value is allowed (zero-length body)")
	void emptyValue() throws Exception {
		mvc.perform(put("/kv/v1/empty").contentType(MediaType.TEXT_PLAIN).content(new byte[0]))
				.andExpect(status().isNoContent());

		mvc.perform(get("/kv/v1/empty")).andExpect(status().isOk()).andExpect(content().bytes(new byte[0]));
	}
}

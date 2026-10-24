package com.saldev.turnovivo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = "app.slots.initialize-on-startup=false")
class DemoApplicationTests {

	@Test
	void contextLoads() {
	}

}

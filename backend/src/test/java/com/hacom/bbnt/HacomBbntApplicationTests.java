package com.hacom.bbnt;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "app.gotenberg.enabled=false")
class HacomBbntApplicationTests {
    @Test
    void contextLoads() {
    }
}

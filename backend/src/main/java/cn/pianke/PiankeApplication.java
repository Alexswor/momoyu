package cn.pianke;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("cn.pianke.mapper")
public class PiankeApplication {
    public static void main(String[] args) {
        SpringApplication.run(PiankeApplication.class, args);
    }
}

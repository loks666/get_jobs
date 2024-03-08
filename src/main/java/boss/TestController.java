package boss;

import com.sun.tools.javac.Main;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.stream.Collectors;

@RestController
public class TestController {

    @GetMapping("/getcookies")
    public String readAllCookies(HttpServletRequest request) {
        String re = "";
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            re = Arrays.stream(cookies)
                    .map(c -> c.getName() + "=" + c.getValue())
                    .collect(Collectors.joining(", "));
        }

        return re;
    }

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.debug("输出DEBUG级别日志");
        log.info("输出INFO级别日志");
        log.warn("输出WARN级别日志");
        log.error("输出ERROR级别日志");
    }

}

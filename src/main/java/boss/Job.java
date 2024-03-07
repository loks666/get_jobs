import lombok.Data;

import java.io.Serializable;

@Data
public class Job implements Serializable {
    private String Href;
    private String jobName;
    private String jobArea;
    private String salary;
    private String tag;
    private String recruiter;
}

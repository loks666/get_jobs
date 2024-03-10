package boss;

import lombok.Data;

import java.io.Serializable;

@Data
public class Job implements Serializable {
    private String href;
    private String jobName;
    private String jobArea;
    private String salary;
    private String tag;
    private String recruiter;
    private String companyName;

    @Override
    public String toString() {
        return String.format("【%s, %s, %s, %s, %s, %s】", companyName, jobName, jobArea, salary, tag, recruiter);
    }
}



package in.adarshr.targetgen.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(of = {"group", "artifact"})
public class Repo {
    private String group;
    private String artifact;
    private String location;
    private String version;

    @Override
    public String toString() {
        return "Repo: " + group + ":" + artifact;
    }
}

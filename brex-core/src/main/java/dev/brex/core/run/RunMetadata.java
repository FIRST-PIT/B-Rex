package dev.brex.core.run;

import dev.brex.core.util.Names;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Descriptive information about a run: which routine, which robot, which code.
 *
 * <p>Everything except the routine name is optional, because a team should be able to start
 * recording with a single line of code.
 */
public final class RunMetadata {

    private final String name;
    private final RunKind kind;
    private final RunSource source;
    private final long startedAtEpochMillis;
    private final String robot;
    private final Map<String, String> robotConfig;
    private final String softwareVersion;
    private final String gitCommit;
    private final String gitBranch;
    private final Boolean gitDirty;
    private final List<String> tags;
    private final Map<String, String> properties;

    private RunMetadata(Builder b) {
        this.name = Names.requireValid("Run name", b.name);
        this.kind = b.kind;
        this.source = b.source;
        this.startedAtEpochMillis = b.startedAtEpochMillis;
        this.robot = b.robot;
        this.robotConfig = Collections.unmodifiableMap(new LinkedHashMap<>(b.robotConfig));
        this.softwareVersion = b.softwareVersion;
        this.gitCommit = b.gitCommit;
        this.gitBranch = b.gitBranch;
        this.gitDirty = b.gitDirty;
        this.tags = Collections.unmodifiableList(new ArrayList<>(b.tags));
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(b.properties));
    }

    /** Starts metadata for a routine, for example {@code blue-left} or {@code match-038}. */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /** The routine name: the autonomous program, or a label such as {@code match-038}. */
    public String name() {
        return name;
    }

    public RunKind kind() {
        return kind;
    }

    public RunSource source() {
        return source;
    }

    /** Wall-clock start time in milliseconds since the Unix epoch, or 0 when unknown. */
    public long startedAtEpochMillis() {
        return startedAtEpochMillis;
    }

    /** Robot identifier, or null. */
    public String robot() {
        return robot;
    }

    /** Free-form configuration such as drivetrain type, gear ratios or tuning constants. */
    public Map<String, String> robotConfig() {
        return robotConfig;
    }

    /** The team's software version, or null. */
    public String softwareVersion() {
        return softwareVersion;
    }

    /** Full or abbreviated Git commit hash of the robot code, or null when unknown. */
    public String gitCommit() {
        return gitCommit;
    }

    public String gitBranch() {
        return gitBranch;
    }

    /** Whether the working tree had uncommitted changes, or null when unknown. */
    public Boolean gitDirty() {
        return gitDirty;
    }

    public List<String> tags() {
        return tags;
    }

    /** Additional team-defined metadata. */
    public Map<String, String> properties() {
        return properties;
    }

    public Builder toBuilder() {
        Builder b = new Builder(name)
                .kind(kind)
                .source(source)
                .startedAtEpochMillis(startedAtEpochMillis)
                .robot(robot)
                .softwareVersion(softwareVersion)
                .gitCommit(gitCommit)
                .gitBranch(gitBranch)
                .gitDirty(gitDirty);
        b.robotConfig.putAll(robotConfig);
        b.tags.addAll(tags);
        b.properties.putAll(properties);
        return b;
    }

    @Override
    public String toString() {
        return "RunMetadata(" + name + ", " + kind + ", " + source + ")";
    }

    /** Builder for {@link RunMetadata}. */
    public static final class Builder {
        private final String name;
        private RunKind kind = RunKind.AUTONOMOUS;
        private RunSource source = RunSource.ROBOT;
        private long startedAtEpochMillis;
        private String robot;
        private final Map<String, String> robotConfig = new LinkedHashMap<>();
        private String softwareVersion;
        private String gitCommit;
        private String gitBranch;
        private Boolean gitDirty;
        private final List<String> tags = new ArrayList<>();
        private final Map<String, String> properties = new LinkedHashMap<>();

        private Builder(String name) {
            this.name = name;
        }

        public Builder kind(RunKind kind) {
            this.kind = kind == null ? RunKind.AUTONOMOUS : kind;
            return this;
        }

        public Builder source(RunSource source) {
            this.source = source == null ? RunSource.ROBOT : source;
            return this;
        }

        public Builder startedAtEpochMillis(long millis) {
            this.startedAtEpochMillis = millis;
            return this;
        }

        public Builder robot(String robot) {
            this.robot = robot;
            return this;
        }

        public Builder robotConfig(String key, String value) {
            this.robotConfig.put(key, value);
            return this;
        }

        public Builder robotConfig(Map<String, String> config) {
            this.robotConfig.putAll(config);
            return this;
        }

        public Builder softwareVersion(String version) {
            this.softwareVersion = version;
            return this;
        }

        public Builder gitCommit(String commit) {
            this.gitCommit = commit;
            return this;
        }

        public Builder gitBranch(String branch) {
            this.gitBranch = branch;
            return this;
        }

        public Builder gitDirty(Boolean dirty) {
            this.gitDirty = dirty;
            return this;
        }

        public Builder tag(String tag) {
            this.tags.add(tag);
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags.addAll(tags);
            return this;
        }

        public Builder property(String key, String value) {
            this.properties.put(key, value);
            return this;
        }

        public Builder properties(Map<String, String> properties) {
            this.properties.putAll(properties);
            return this;
        }

        public RunMetadata build() {
            return new RunMetadata(this);
        }
    }
}

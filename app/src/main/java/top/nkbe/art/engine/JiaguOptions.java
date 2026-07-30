package top.nkbe.art.engine;

/**
 * Thread-safe configuration options for JiaguEngine hardening execution.
 * All optional features default to OFF for NPatch integration compatibility.
 */
public class JiaguOptions {
    private String soName = "ArkStub";
    private String stubClassName = "com.ark.safe.StubApp";
    private String savePath = "";
    private boolean autoSign = false; // Default OFF
    private int fake360Type = 0; // Default 0 (OFF)
    private boolean useCustomJks = false;
    private String jksPath = "";
    private String jksStorePass = "";
    private String jksAlias = "";
    private String jksKeyPass = "";
    private boolean enableStrDec = false;

    public JiaguOptions() {}

    public JiaguOptions(
            String soName,
            String stubClassName,
            String savePath,
            boolean autoSign,
            int fake360Type,
            boolean useCustomJks,
            String jksPath,
            String jksStorePass,
            String jksAlias,
            String jksKeyPass
    ) {
        this.soName = soName != null ? soName : "ArkStub";
        this.stubClassName = stubClassName != null ? stubClassName : "com.ark.safe.StubApp";
        this.savePath = savePath != null ? savePath : "";
        this.autoSign = autoSign;
        this.fake360Type = fake360Type;
        this.useCustomJks = useCustomJks;
        this.jksPath = jksPath != null ? jksPath : "";
        this.jksStorePass = jksStorePass != null ? jksStorePass : "";
        this.jksAlias = jksAlias != null ? jksAlias : "";
        this.jksKeyPass = jksKeyPass != null ? jksKeyPass : "";
    }

    public String getSoName() {
        return soName;
    }

    public void setSoName(String soName) {
        this.soName = soName;
    }

    public String getStubClassName() {
        return stubClassName;
    }

    public void setStubClassName(String stubClassName) {
        this.stubClassName = stubClassName;
    }

    public String getSavePath() {
        return savePath;
    }

    public void setSavePath(String savePath) {
        this.savePath = savePath;
    }

    public boolean isAutoSign() {
        return autoSign;
    }

    public void setAutoSign(boolean autoSign) {
        this.autoSign = autoSign;
    }

    public int getFake360Type() {
        return fake360Type;
    }

    public void setFake360Type(int fake360Type) {
        this.fake360Type = fake360Type;
    }

    public boolean isUseCustomJks() {
        return useCustomJks;
    }

    public void setUseCustomJks(boolean useCustomJks) {
        this.useCustomJks = useCustomJks;
    }

    public String getJksPath() {
        return jksPath;
    }

    public void setJksPath(String jksPath) {
        this.jksPath = jksPath;
    }

    public String getJksStorePass() {
        return jksStorePass;
    }

    public void setJksStorePass(String jksStorePass) {
        this.jksStorePass = jksStorePass;
    }

    public String getJksAlias() {
        return jksAlias;
    }

    public void setJksAlias(String jksAlias) {
        this.jksAlias = jksAlias;
    }

    public String getJksKeyPass() {
        return jksKeyPass;
    }

    public void setJksKeyPass(String jksKeyPass) {
        this.jksKeyPass = jksKeyPass;
    }

    public boolean isEnableStrDec() {
        return enableStrDec;
    }

    public void setEnableStrDec(boolean enableStrDec) {
        this.enableStrDec = enableStrDec;
    }
}

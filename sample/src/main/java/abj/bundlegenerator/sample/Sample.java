package abj.bundlegenerator.sample;

import abj.bundlegenerator.processor.BundleGenerator;
import abj.bundlegenerator.processor.BundleSet;

@BundleGenerator
public class Sample {

    @BundleSet
    public int getId() {
        return 1;
    }

    @BundleSet
    public String getTag() {
        return "sample";
    }

    @BundleSet
    public boolean isEnabled() {
        return true;
    }
}

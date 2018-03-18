package abj.bundlegenerator.sample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Sample target = new Sample();
        // To Bundle
        final Bundle bundle = SampleBundleGenerator.bundle(target);
        // Restore
        final SampleBundleGenerator.Wrapper restore = SampleBundleGenerator.restore(bundle);
    }
}

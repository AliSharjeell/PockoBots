package dev.pocket.agent;

/** Isolated QA host: never starts the runtime or loads an owner's conversations. */
public class UiMotionRegressionActivity extends androidx.activity.ComponentActivity {
    @Override public void onCreate(android.os.Bundle saved){super.onCreate(saved);AgentStyle.applyTheme(this);setContentView(new android.widget.FrameLayout(this));}
}

package dev.pocket.agent;

import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.util.function.Consumer;

/** Shared local-search control; typing filters existing data without network requests. */
final class AgentSearchBar extends FrameLayout {
    AgentSearchBar(BotsActivity app,String hint,String initial,Consumer<String> changed){
        super(app);setFocusableInTouchMode(true);setDescendantFocusability(FOCUS_BEFORE_DESCENDANTS);LinearLayout row=app.row();row.setPadding(app.dp(8),0,app.dp(4),0);
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,app.dp(46));layout.setMargins(app.dp(4),app.dp(4),app.dp(4),app.dp(8));setLayoutParams(layout);
        AgentIcon icon=new AgentIcon(app,"search","",()->{});icon.setBackground(null);icon.setIconTint(AgentStyle.TEXT);icon.setClickable(false);icon.setFocusable(false);icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(icon,new LinearLayout.LayoutParams(app.dp(30),app.dp(38)));
        EditText input=new EditText(app);AgentStyle.input(input);input.setBackground(null);input.setPadding(app.dp(6),0,app.dp(6),0);input.setTextColor(AgentStyle.TEXT);input.setHintTextColor(AgentStyle.MUTED);input.setSingleLine(true);input.setHint(hint);input.setContentDescription(hint);input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);input.setText(initial);row.addView(input,new LinearLayout.LayoutParams(0,-1,1));
        AgentIcon clear=new AgentIcon(app,"close","Clear search",()->input.setText(""));clear.setBackground(null);clear.setIconTint(AgentStyle.TEXT);clear.setVisibility(initial.isEmpty()?INVISIBLE:VISIBLE);row.addView(clear,new LinearLayout.LayoutParams(app.dp(38),app.dp(38)));
        addView(GlassControls.wrap(row,24f,false),new FrameLayout.LayoutParams(-1,-1));
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence value,int start,int before,int count){clear.setVisibility(value.length()==0?INVISIBLE:VISIBLE);changed.accept(value.toString());}public void afterTextChanged(Editable value){}});
        input.setOnEditorActionListener((view,action,event)->{if(action!=EditorInfo.IME_ACTION_SEARCH)return false;app.getSystemService(InputMethodManager.class).hideSoftInputFromWindow(input.getWindowToken(),0);input.clearFocus();return true;});
    }
}

package dev.pocket.agent;

import android.app.Dialog;
import android.graphics.Typeface;
import android.os.Build;
import android.text.*;
import android.view.*;
import android.widget.*;

/** Full-screen native project editor with the app's toolbar, theme and save flow. */
final class ProjectEditor {
    static void open(BotsActivity app,String project,String path,String source,boolean fresh,Runnable afterClose){
        Dialog dialog=new Dialog(app,android.R.style.Theme_Material_NoActionBar);dialog.setCancelable(false);
        LinearLayout page=app.column();page.setBackgroundColor(AgentStyle.BACK);
        page.setOnApplyWindowInsetsListener((view,insets)->{view.setPadding(app.dp(12)+insets.getSystemWindowInsetLeft(),app.dp(8)+insets.getSystemWindowInsetTop(),app.dp(12)+insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
        LinearLayout bar=app.row();LinearLayout labels=app.column();
        TextView title=app.text(path,17);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);title.setTypeface(AgentStyle.font(app),Typeface.BOLD);labels.addView(title);
        TextView status=app.muted(fresh?"New file":"Saved",11);labels.addView(status);
        EditText editor=new EditText(app);AgentStyle.input(editor);editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);editor.setTypeface(Typeface.MONOSPACE);editor.setLetterSpacing(0);editor.setTextSize(14);editor.setGravity(Gravity.TOP|Gravity.START);editor.setHorizontallyScrolling(true);editor.setBackgroundColor(AgentStyle.BACK);editor.setText(source);editor.setPadding(app.dp(8),app.dp(16),app.dp(8),app.dp(20));editor.setSelectAllOnFocus(false);
        String[] saved={source};boolean[] saving={false};boolean[] exists={!fresh};
        Runnable close=()->{
            if(saving[0])return;
            if(editor.getText().toString().equals(saved[0])){dialog.dismiss();return;}
            AgentSheet confirm=new AgentSheet(app,"Discard changes?");confirm.content.addView(app.muted("Your unsaved changes to this file will be lost.",13));
            confirm.action("Keep editing",confirm.dialog::dismiss,true);confirm.action("Discard changes",()->{confirm.dialog.dismiss();dialog.dismiss();},false);confirm.show();
        };
        bar.addView(GlassControls.wrap(new AgentIcon(app,"back","Close editor",close),22f,false),new LinearLayout.LayoutParams(app.dp(42),app.dp(42)));
        bar.addView(labels,new LinearLayout.LayoutParams(0,-2,1));Button save=new Button(app);save.setText("Save");AgentStyle.button(save,true);bar.addView(GlassControls.wrap(save,22f,true));save.setEnabled(fresh);
        Runnable update=()->{boolean changed=!exists[0]||!editor.getText().toString().equals(saved[0]);status.setText(saving[0]?"Saving...":!exists[0]?"New file":changed?"Unsaved changes":"Saved");save.setEnabled(changed&&!saving[0]);};
        editor.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){update.run();}public void afterTextChanged(Editable value){}});
        save.setOnClickListener(view->{String content=editor.getText().toString();saving[0]=true;update.run();app.marketplaceRequest("PUT","/file",app.json("projectId",project,"path",path,"content",content),value->{saved[0]=content;exists[0]=true;saving[0]=false;update.run();},error->{saving[0]=false;update.run();AgentSheet.message(app,"Couldn't save",error);});});
        page.addView(bar,new LinearLayout.LayoutParams(-1,app.dp(60)));View rule=new View(app);rule.setBackgroundColor(AgentStyle.BORDER);page.addView(rule,new LinearLayout.LayoutParams(-1,app.dp(1)));page.addView(editor,new LinearLayout.LayoutParams(-1,0,1));
        GlassControls.bindDialog(dialog,app);dialog.setContentView(page);dialog.setOnKeyListener((value,key,event)->{if(key!=android.view.KeyEvent.KEYCODE_BACK)return false;if(event.getAction()==android.view.KeyEvent.ACTION_UP)close.run();return true;});dialog.setOnDismissListener(value->afterClose.run());dialog.show();
        Window window=dialog.getWindow();if(window!=null){window.setLayout(-1,-1);window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(AgentStyle.BACK));window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);androidx.core.view.WindowInsetsControllerCompat bars=new androidx.core.view.WindowInsetsControllerCompat(window,window.getDecorView());bars.setAppearanceLightStatusBars(!AgentStyle.dark);bars.setAppearanceLightNavigationBars(!AgentStyle.dark);}
    }
}

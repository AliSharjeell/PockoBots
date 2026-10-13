package dev.pocket.agent;

import android.content.Context;
import java.io.*;
import java.security.MessageDigest;

/** Small APK backend overlay: updating the agent does not unpack Linux again. */
final class PackagedBackend {
    static File prepare(Context context) throws Exception {
        String[] names={"server.js","terminal_bridge.py"};
        MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[][] payload=new byte[2][];
        for(int i=0;i<names.length;i++)try(InputStream in=context.getAssets().open("backend/"+names[i])){payload[i]=Io.bytes(in);digest.update(payload[i]);}
        StringBuilder key=new StringBuilder();for(byte b:digest.digest())key.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        File root=new File(context.getFilesDir(),"app-backends/"+key);
        if(!new File(root,".ready").isFile()){
            new File(root,"dist").mkdirs();new File(root,"runtime").mkdirs();
            for(int i=0;i<names.length;i++)try(OutputStream out=new FileOutputStream(new File(root,(i==0?"dist/":"runtime/")+names[i]))){out.write(payload[i]);}
            Io.write(new File(root,".ready"),key.toString());
        }
        return root;
    }
}

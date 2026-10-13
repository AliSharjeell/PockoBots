package dev.pocket.agent;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Exercises the real vision adapter and Artemis helper, without paid credentials. */
final class MobileMockModel implements AutoCloseable {
    private final ServerSocket socket=new ServerSocket(0,4,InetAddress.getByName("127.0.0.1"));
    volatile Exception failure;volatile int stage;volatile boolean sawImage;private volatile boolean running=true;
    MobileMockModel() throws IOException {
        Thread worker=new Thread(() -> {while(running)try(Socket connection=socket.accept()){
            connection.setSoTimeout(15000);InputStream input=connection.getInputStream();line(input);
            int length=0;String header;while(!(header=line(input)).isEmpty())if(header.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(header.substring(15).trim());
            if(length<=0||length>8000000)throw new IOException("Invalid model request length");
            byte[] body=new byte[length];int read=0;while(read<length){int n=input.read(body,read,length-read);if(n<0)throw new EOFException();read+=n;}
            String raw=new String(body,StandardCharsets.UTF_8);if(raw.contains("image_url"))sawImage=true;
            JSONArray messages=new JSONObject(raw).getJSONArray("messages");JSONObject screen=null;
            for(int i=messages.length()-1;i>=0&&screen==null;i--){Object content=messages.getJSONObject(i).opt("content");String value="";
                if(content instanceof String)value=(String)content;else if(content instanceof JSONArray){JSONArray parts=(JSONArray)content;for(int j=0;j<parts.length();j++)if(parts.optJSONObject(j)!=null&&parts.optJSONObject(j).optString("type").equals("text"))value+=parts.getJSONObject(j).optString("text");}
                try{JSONObject candidate=new JSONObject(value);if(candidate.has("screen_id"))screen=candidate;}catch(JSONException ignored){}
            }
            int turn=stage++;String name;JSONObject args=new JSONObject();
            if(turn==0||turn==2||turn==4||turn==6)name="observe_screen";
            else if(turn==1){name="manage_app";args.put("screen_id",screen.getString("screen_id")).put("app_name","com.android.settings");}
            else if(turn==3){name="click";int target=-1;JSONArray elements=screen.getJSONArray("elements");for(int i=0;i<elements.length();i++)if(elements.getJSONObject(i).optString("text").equals("Battery")){target=elements.getJSONObject(i).getInt("index");break;}
                if(target<0){stage=2;name="wait_for_delay";args.put("milliseconds",750);}
                else args.put("screen_id",screen.getString("screen_id")).put("target",target);}
            else if(turn==5){name="ask_user";args.put("question","Battery is open. Say ready to finish.");}
            else{name="report_task_status";args.put("status","completed").put("summary","Battery screen verified through Artemis on this device.");}
            JSONObject function=new JSONObject().put("name",name).put("arguments",args.toString());
            JSONObject call=new JSONObject().put("index",0).put("id","phone-"+turn).put("type","function").put("function",function);
            String payload="data: "+chunk(new JSONObject().put("tool_calls",new JSONArray().put(call)),JSONObject.NULL)+"\n\n"+"data: "+chunk(new JSONObject(),"tool_calls")+"\n\ndata: [DONE]\n\n";
            byte[] bytes=payload.getBytes(StandardCharsets.UTF_8);OutputStream output=connection.getOutputStream();
            output.write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));output.write(bytes);output.flush();
        }catch(Exception e){if(running)failure=e;}},"mobile-model");worker.setDaemon(true);worker.start();
    }
    private static String line(InputStream input)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();int b;while((b=input.read())!=-1&&b!='\n'){if(b!='\r')out.write(b);if(out.size()>16000)throw new IOException("Header too long");}if(b<0)throw new EOFException();return out.toString("UTF-8");}
    private JSONObject chunk(JSONObject delta,Object finish)throws JSONException{return new JSONObject().put("id","phone").put("object","chat.completion.chunk").put("created",123).put("model","mobile-mock").put("choices",new JSONArray().put(new JSONObject().put("index",0).put("delta",delta).put("finish_reason",finish)));}
    String baseUrl(){return "http://127.0.0.1:"+socket.getLocalPort()+"/v1";}
    @Override public void close()throws IOException{running=false;socket.close();}
}

package dev.pocket.agent;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Local provider and web page: real SDK/MCP/Artemis actions, no paid provider calls. */
final class BotModelFixture implements AutoCloseable {
    private final ServerSocket model=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1")),web=new ServerSocket(0,4,InetAddress.getByName("127.0.0.1"));
    volatile Exception failure;volatile boolean sawMcp,sawBrowser,sawUnified;private volatile boolean running=true;private int browserStage;
    BotModelFixture()throws IOException{
        Thread page=new Thread(() -> {while(running)try(Socket socket=web.accept()){InputStream input=socket.getInputStream();while(!line(input).isEmpty()){}byte[] body="<!doctype html><html><body><h1>Pocket browser check completed</h1><p>This local page is open in the real phone browser.</p></body></html>".getBytes(StandardCharsets.UTF_8);respond(socket,"text/html",body);}catch(Exception e){if(running)failure=e;}},"bot-page");page.setDaemon(true);page.start();
        Thread worker=new Thread(() -> {while(running)try(Socket socket=model.accept()){
            socket.setSoTimeout(20000);InputStream input=socket.getInputStream();line(input);String header;int size=0;while(!(header=line(input)).isEmpty())if(header.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:"))size=Integer.parseInt(header.substring(15).trim());
            if(size<1||size>10000000)throw new IOException("Unexpected request size");byte[] bytes=new byte[size];int at=0;while(at<size){int n=input.read(bytes,at,size-at);if(n<0)throw new EOFException();at+=n;}
            JSONObject request=new JSONObject(new String(bytes,StandardCharsets.UTF_8));JSONArray messages=request.getJSONArray("messages");JSONObject last=messages.getJSONObject(messages.length()-1);boolean phone=request.optJSONArray("tools").toString().contains("open_url")&&messages.toString().contains("BROWSER_FIXTURE");JSONObject delta=new JSONObject();String finish="stop";
            if(phone){JSONObject screen=screen(messages);String name;JSONObject args=new JSONObject();int turn=browserStage++;
                if(turn==0||turn%2==0)name="observe_screen";
                else if(turn==1){name="open_url";args.put("screen_id",screen.getString("screen_id")).put("url",pageUrl());}
                else{JSONArray elements=screen.getJSONArray("elements");boolean verified=false;int welcome=-1;for(int i=0;i<elements.length();i++){String label=elements.getJSONObject(i).optString("text");if(label.contains("Pocket browser check completed"))verified=true;if(label.equals("Use without an account")||label.equals("Stay signed out")||label.equals("Continue without an account")||label.equals("Accept & continue")||label.equals("No thanks")||label.equals("Got it"))welcome=elements.getJSONObject(i).optInt("index");}
                    if(verified){sawBrowser=true;name="report_task_status";args.put("status","completed").put("summary","Browser page verified through Artemis.");}
                    else if(welcome>=0){name="click";args.put("screen_id",screen.getString("screen_id")).put("target",welcome);}
                    else{name="open_url";args.put("screen_id",screen.getString("screen_id")).put("url",pageUrl());Thread.sleep(600);}}
                delta=tool(name,args,"browser-"+turn);finish="tool_calls";
            }else if(last.optString("role").equals("user")&&last.toString().contains("BROWSER_FIXTURE")){
                sawUnified=true;delta=tool("use_phone",new JSONObject().put("task","BROWSER_FIXTURE: open and verify "+pageUrl()),"unified-phone");finish="tool_calls";
            }else if(last.optString("role").equals("user")){
                String name="write_file";JSONArray tools=request.getJSONArray("tools");for(int i=0;i<tools.length();i++){String candidate=tools.getJSONObject(i).getJSONObject("function").getString("name");if(candidate.startsWith("mcp_")){name=candidate;sawMcp=true;break;}}
                delta=tool(name,name.equals("write_file")?new JSONObject().put("path","src/bot.txt").put("content","Created by my local coding bot"):new JSONObject(),"bot-tool");finish="tool_calls";
            }else delta.put("content",sawMcp&&request.getJSONArray("tools").toString().contains("mcp_")?"Read the local MCP note successfully.":"**Created** `src/bot.txt` in my workspace.\n\n| File | Status |\n| --- | --- |\n| bot.txt | Saved |\n\n[Open preview]("+pageUrl()+")");
            String payload="data: "+chunk(delta,JSONObject.NULL)+"\n\ndata: "+chunk(new JSONObject(),finish)+"\n\ndata: [DONE]\n\n";respond(socket,"text/event-stream",payload.getBytes(StandardCharsets.UTF_8));
        }catch(Exception e){if(running)failure=e;}},"bot-model");worker.setDaemon(true);worker.start();
    }
    private static JSONObject screen(JSONArray messages)throws JSONException{for(int i=messages.length()-1;i>=0;i--){Object content=messages.optJSONObject(i).opt("content");String text=content instanceof String?(String)content:"";if(content instanceof JSONArray){JSONArray parts=(JSONArray)content;for(int j=0;j<parts.length();j++){JSONObject part=parts.optJSONObject(j);if(part!=null&&part.optString("type").equals("text"))text+=part.optString("text");}}try{JSONObject value=new JSONObject(text);if(value.has("screen_id"))return value;}catch(Exception ignored){}}return null;}
    private static JSONObject tool(String name,JSONObject args,String id)throws JSONException{return new JSONObject().put("tool_calls",new JSONArray().put(new JSONObject().put("index",0).put("id",id).put("type","function").put("function",new JSONObject().put("name",name).put("arguments",args.toString()))));}
    private static JSONObject chunk(JSONObject delta,Object finish)throws JSONException{return new JSONObject().put("id","bot-fixture").put("object","chat.completion.chunk").put("created",1).put("model","fixture").put("choices",new JSONArray().put(new JSONObject().put("index",0).put("delta",delta).put("finish_reason",finish)));}
    private static String line(InputStream input)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();int b;while((b=input.read())!=-1&&b!='\n'){if(b!='\r')out.write(b);if(out.size()>16000)throw new IOException("Header too long");}if(b<0)throw new EOFException();return out.toString("UTF-8");}
    private static void respond(Socket socket,String type,byte[] body)throws IOException{OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Type: "+type+"\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));out.write(body);out.flush();}
    String baseUrl(){return "http://127.0.0.1:"+model.getLocalPort()+"/v1";}String pageUrl(){return "http://127.0.0.1:"+web.getLocalPort()+"/";}
    @Override public void close()throws IOException{running=false;model.close();web.close();}
}

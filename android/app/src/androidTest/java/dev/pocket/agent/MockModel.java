package dev.pocket.agent;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Local provider fixture: exercises pi's real adapter and tool loop without a paid key. */
final class MockModel implements AutoCloseable {
    private final ServerSocket socket=new ServerSocket(0,4,InetAddress.getByName("127.0.0.1"));
    volatile Exception failure;
    private volatile boolean running=true;
    MockModel() throws IOException {
        Thread worker=new Thread(() -> {
            while(running)try(Socket connection=socket.accept()){
                BufferedReader reader=new BufferedReader(new InputStreamReader(connection.getInputStream(),StandardCharsets.UTF_8));
                String first=reader.readLine();if(first==null)continue;
                int length=0;String header;
                while((header=reader.readLine())!=null&&!header.isEmpty())if(header.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(header.substring(15).trim());
                char[] body=new char[length];int read=0;while(read<length){int n=reader.read(body,read,length-read);if(n<0)throw new EOFException();read+=n;}
                JSONObject request=new JSONObject(new String(body));JSONArray messages=request.getJSONArray("messages");JSONObject last=messages.getJSONObject(messages.length()-1);
                JSONObject delta;String finish;
                if(last.getString("role").equals("user")){
                    JSONObject args=new JSONObject().put("path","src/agent.txt").put("content","Created by the packaged Android agent");
                    JSONObject function=new JSONObject().put("name","write_file").put("arguments",args.toString());
                    JSONObject call=new JSONObject().put("index",0).put("id","android-write").put("type","function").put("function",function);
                    delta=new JSONObject().put("tool_calls",new JSONArray().put(call));finish="tool_calls";
                }else{delta=new JSONObject().put("content","File created on your phone.");finish="stop";}
                String payload="data: "+chunk(delta,JSONObject.NULL)+"\n\n"+"data: "+chunk(new JSONObject(),finish)+"\n\ndata: [DONE]\n\n";
                byte[] bytes=payload.getBytes(StandardCharsets.UTF_8);
                OutputStream output=connection.getOutputStream();output.write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));output.write(bytes);output.flush();
            }catch(Exception e){if(running)failure=e;}
        },"mock-model");worker.setDaemon(true);worker.start();
    }
    private JSONObject chunk(JSONObject delta,Object finish) throws JSONException {
        return new JSONObject().put("id","android-mock").put("object","chat.completion.chunk").put("created",123).put("model","android-mock")
            .put("choices",new JSONArray().put(new JSONObject().put("index",0).put("delta",delta).put("finish_reason",finish)));
    }
    String baseUrl(){return "http://127.0.0.1:"+socket.getLocalPort()+"/v1";}
    @Override public void close() throws IOException {running=false;socket.close();}
}

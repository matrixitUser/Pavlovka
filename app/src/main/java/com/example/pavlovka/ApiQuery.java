package com.example.pavlovka;

import android.content.Context;

import com.example.pavlovka.Classes.Auth.AuthBySession.AuthBySession;
import com.example.pavlovka.Classes.GetSessionidd.BodyFromSession;
import com.example.pavlovka.Classes.GetSessionidd.SessionJson;
import com.example.pavlovka.Classes.GetSessionidd.UserFromBodySession;
import com.example.pavlovka.Classes.Message;
import com.example.pavlovka.Classes.Poll;
import com.example.pavlovka.Classes.QueryFromDatabase.QueryDB;
import com.example.pavlovka.Classes.QueryFromDatabase.RecordsFromQueryDB;
import com.example.pavlovka.Classes.SignalBind;
import com.google.gson.Gson;

import java.io.IOException;
import java.lang.String;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import retrofit2.Call;
import retrofit2.Response;

public class ApiQuery {

    private static ApiQuery instance = new ApiQuery();

    public static ApiQuery Instance() {
        return instance;
    }
    public Gson gson = new Gson();
    public String gSessionId = "";
    MyService myService = new MyService();
    ArrayList<String> logsList = new ArrayList<>();
    boolean isAdmin = false;
    public String AuthByLogin(Context context, String login, String password){
        SessionJson sessionJson = new SessionJson();
        sessionJson.setWhat("auth-by-login");
        sessionJson.setLoginPassword(login, Helper.getMd5(password));

        String json = gson.toJson(sessionJson);
        Call<SessionJson> call = NetworkService.Instance().getMxApi().getSessionId(json);
        try {
            Response<SessionJson> response = call.execute();
            if(!response.isSuccessful()){
                return "";
            }
            BodyFromSession post = response.body().getBody();
            if(post == null) return "";
            UserFromBodySession userFromBodySession = post.getUser();
            if(userFromBodySession != null){
                String strIsAdmin = userFromBodySession.getIsAdmin();
                if(strIsAdmin == null || strIsAdmin.equals("false")){
                    isAdmin = false;
                }
                else {
                    isAdmin = true;
                }
            }
            gSessionId = post.getSessionId();
            Util.setPropertyConfig("sessionId", gSessionId, context);
            Util.setPropertyConfig("isAdmin", Boolean.toString(isAdmin), context);
            return post.getSessionId();
        } catch (IOException e) {
            if(logsReplay(e.getMessage())){
                Util.logsException(e.getMessage(),context);
            }
        }
       return "";
    }
    public boolean logsReplay(String value){
        for(int i = 0; i < logsList.size(); i++){
            if(logsList.get(i).equals(value)){
                return false;
            }
        }
        if(logsList.size() > 4){
            logsList.remove(0);
        }
        logsList.add(value);
        return true;
    }
    public Message MessageExecute (String what, Object body, Context context){
        Message message = new Message();
        message.setHead(what, gSessionId);
        message.setBody(body);
        String json = gson.toJson(message);
        Message answerMessage = new Message();
        Call<Message> call = NetworkService.Instance().getMxApi().message(json);
        try {
            Response<Message> response = call.execute();
            if(!response.isSuccessful()) return null;
            answerMessage = response.body();
        } catch (IOException e) {
            if(logsReplay(e.getMessage())){
                Util.logsException(e.getMessage(),context);
            }
        }
        return answerMessage;
    }

    public void SignalBind(String connectionId, Context context){
        SignalBind signalBind = new SignalBind();
        signalBind.setConnectionId(connectionId);
        MessageExecute("signal-bin", signalBind, context);
    }

    public Boolean Poll(String objectId, String cmd, String components, Context context){
        if(isAdmin == false){
        try {
            isAdmin = Boolean.parseBoolean(Util.getProperty("isAdmin", context));
        } catch (IOException exc) {
            Util.logsException(exc.getMessage(), context);
        }}
        if(!isAdmin) return true;
        Poll poll = new Poll();
        poll.setPoll1(new String[]{objectId}, cmd, components);
        Message answer = MessageExecute("poll", poll, context);
        if(answer == null || answer.getHead() == null)
        {
            boolean isNotConnection = false;
            try{
                isNotConnection = Boolean.parseBoolean(Util.getPropertyOrSetDefaultValue("isNotConnection", "false",context));
            }
            catch (Exception ex){
                ex.fillInStackTrace();
            }
            myService.sendNotif(Const.notConnectionToServer, "", Const.notifNotConnecion, isNotConnection);
            return false;
        }
        String what = answer.getHead().getWhat();
        if(what.equals("poll-accepted")){
            try {
                Thread.sleep(1000 * 3);
                return true;
            } catch (InterruptedException exc) {
                if(logsReplay(exc.getMessage())){
                    Util.logsException(exc.getMessage(),context);
                }
            }
        }
        return false;
    }

    public String isGetSession(Context context) {
        String login = "", password = "";
        try {
            login = Util.getProperty("login", context);
            password = Util.getProperty("password", context);
            gSessionId = Util.getProperty("sessionId", context);
        } catch (IOException exc) {
            Util.logsException(exc.getMessage(), context);
        }
        if(gSessionId == null || gSessionId.equals("")) return AuthByLogin(context, login, password);
        if(AuthBySession(context)) return gSessionId;
        return AuthByLogin(context, login, password);
    }

    public boolean AuthBySession(Context context)
    {
        if (gSessionId == null || gSessionId.equals("")) return false;
        AuthBySession authBySession = new AuthBySession();
        authBySession.setSessionId(gSessionId);
        try {
            Message message = MessageExecute("auth-by-session", authBySession, context);
            if(message == null || message.getHead() == null) return false;
            if (!message.getHead().getWhat().equals("auth-success")) return false;
            String json = gson.toJson(message.getBody());
            AuthBySession authBySession1 = gson.fromJson(json, AuthBySession.class) ;
            gSessionId = authBySession1.getSessionId();
            Util.setPropertyConfig("sessionId", gSessionId, context);
            return true;
        } catch (Exception e) {
            if(logsReplay(e.getMessage())){
                Util.logsException(e.getMessage(),context);
            }
        }
        return false;
    }

    public RecordsFromQueryDB[] QueryFromDatabase(Context context){
        Calendar calStart = Calendar.getInstance();
        calStart.add(Calendar.MINUTE,-25);
        Date dtStart = calStart.getTime();
        Calendar calendarNow = Calendar.getInstance();
        calendarNow.add(Calendar.MINUTE, 20);
        Date dtNow = calendarNow.getTime();
        QueryDB queryDB = new QueryDB();
        queryDB.setQueryDB(new String[]{Const.objectIdUpp}, dtStart, dtNow,"Current");
        try {
            Message message = MessageExecute("records-get1", queryDB, context);
            if(message == null || message.getBody() == null) return null;
            String json1 = gson.toJson(message.getBody());
            QueryDB queryDB1 = gson.fromJson(json1, QueryDB.class) ;
            return queryDB1.getRecords();
        } catch (Exception e) {
            if(logsReplay(e.getMessage())){
                Util.logsException(e.getMessage(),context);
            }
        }
        return null;
    }
}

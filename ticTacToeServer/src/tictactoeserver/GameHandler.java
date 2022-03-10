/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tictactoeserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.stage.Stage;
import model.Game;
import model.PlayerDto;
import serverdao.ConnectionDB;
import serverdao.PlayerManager;
import serverdao.PlayerManagerImpl;
import utils.AuthenticationConstants;
import utils.GameResult;
import utils.PlayerStatusValues;
import utils.ResultConstants;
import utils.Role;
import utils.ServerQueries;

/**
 *
 * @author AhmedAli
 */
public class GameHandler extends Thread {

    InputStreamReader dis = null;
    PrintStream ps = null;
    Socket s;
    BufferedReader bufferReader;
    StringTokenizer stringTokenizer;
    PlayerManager playerMgr = PlayerManagerImpl.getInstance(ConnectionDB.getInstance());
    PlayerDto currentPlayer;
    static Map<String, PlayerDto> sessions = Collections.synchronizedMap(new HashMap());
    static Vector<Game> currentGames = new Vector<>();
    boolean flag = true;
    
    private String id = UUID.randomUUID().toString();
    private static Vector<GameHandler> inActiveSession = new Vector<>();
    
    public GameHandler(Socket cs, Stage stage) {
        try {
            this.s = cs;
            dis = new InputStreamReader(s.getInputStream());
            ps = new PrintStream(s.getOutputStream());
            bufferReader = new BufferedReader(dis);
            GameHandler.inActiveSession.add(this);
            start();
        } catch (IOException ex) {
            releasePlayersResources();
            Logger.getLogger(GameHandler.class.getName()).log(Level.SEVERE, null, ex);
        }

        handleClosingServer(stage);
    }

    @Override
    public void run() {
        while (flag) {
            try {
                String msg = bufferReader.readLine();
                if (msg != null) {
                    System.out.println(currentPlayer == null ? "null player     " + msg : currentPlayer.getUsername() + "    " + msg);
                    checkCommand(msg);
                }
//                sendMessageToAll(msg);
            } catch (SocketException ex) {
                flag = false;
                closeStreamAbnormally();
                    //remove player from sessions //change state in db 
                Logger.getLogger(GameHandler.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(GameHandler.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

//    void sendMessageToAll(String msg){
//        for(int i=0; i<clientsVector.size(); i++) {
//           clientsVector.get(i).ps.println(msg);
//        }
//    }
    private void closeStream() {
        try {
            ps.println(ServerQueries.CLOSE_NORMALLY);
            stopServerTalkingAndListening();
            updatePlayerStateDB();
            releasePlayerAndStopThreading();
        } catch (IOException ex1) {
            Logger.getLogger(GameHandler.class.getName()).log(Level.SEVERE, null, ex1);
        }
    }
    private void closeStreamAbnormally() {
        releasePlayersResources();
    } 
    private void releasePlayersResources() {
        try {
            stopServerTalkingAndListening();
            //TODO should be in Thread but take care of refrence!
            updatePlayerStateDB();
            sessions.remove(currentPlayer.getUsername());
            currentGames.remove(new Game(currentPlayer, currentPlayer));
            releasePlayerAndStopThreading();
        } catch (IOException ex1) {
            Logger.getLogger(GameHandler.class.getName()).log(Level.SEVERE, null, ex1);
        }
    } 
    
    private void stopServerTalkingAndListening() throws IOException {
        flag = false;
        dis.close();
        ps.close();
        bufferReader.close();
        s.close();
    }
    private void updatePlayerStateDB() {
        playerMgr.updatePlayerStatusOnDB(
            currentPlayer.getUsername(), 
            PlayerStatusValues.CLOSING, 
            currentPlayer.getStatus());
    }
    private void releasePlayerAndStopThreading() {
        currentPlayer = null;            
        stop();
    }
    
    private void registerNewPlayer(String username, String password) {
        new Thread() {
            @Override
            public void run() {
                int result = playerMgr.addNewPlayer(username, password, 0, false);
                switch (result) {
                    case ResultConstants.SUCCESSFULLY_REGISTERED:
                        ps.println(AuthenticationConstants.SUCCESS_REGISTERED);
                        break;
                    case ResultConstants.ALREADY_REGISTERED:
                        ps.println(AuthenticationConstants.REGISTER_FAILED_ALREADY_REGISTERED);
                        break;
                    case ResultConstants.DB_ERROR:
                        ps.println(AuthenticationConstants.PROBLEM_IN_SERVER);
                        break;
                    default:
                        System.out.println("ERORR UNKOWN VALUE == " + result);
                }
            }
        }.start();
    }

    private void playerLogin(String username, String password) {
        System.out.println("USERNAME: " + username);
        System.out.println("PASSWORD: " + password);
        new Thread() {
            @Override
            public void run() {
                Integer scoreRefrence = 0;
                switch (playerMgr.login(username, password, scoreRefrence)) {
                    case ResultConstants.SUCCESSFULLY_LOGGINED:
                        currentPlayer = new PlayerDto(username, "", scoreRefrence, true , GameHandler.this);
                        ps.println(AuthenticationConstants.SUCCESS_LOGIN.concat(";").concat(username).concat(";").concat(String.valueOf(scoreRefrence)));
                        sessions.put(username, currentPlayer);
                        GameHandler.inActiveSession.remove(GameHandler.this);
                        onAvailablePlayersChangee(playerMgr.getAvilableOnlinePlayersWithScores());
                        break;
                    case ResultConstants.WRONG_USERNAME_OR_PASSWORD:
                        ps.println(AuthenticationConstants.WRONG_USERNAME_OR_PASSWORD);
                        break;
                    case ResultConstants.ALREADY_LOGGINED:
                        ps.println(AuthenticationConstants.ALREADY_LOGINED_ON_ANOTHER_DEVICE);
                        break;
                    case ResultConstants.DB_ERROR:
                        ps.println(AuthenticationConstants.PROBLEM_IN_SERVER);
                        break;
                    default:
                        System.out.println("ERROR: Unknown message");
                }
            }
        }.start();
    }

    private void handleClosingServer(Stage stage) {
        stage.setOnCloseRequest((event) -> {
            closeStream();
            System.exit(0);
        });
    }

    private void closeNormally(String username) {
        playerMgr.logOut(username, currentPlayer.getStatus());
        currentPlayer.setStatus(PlayerStatusValues.CLOSING);
    }

    private void checkCommand(String msg) {
        stringTokenizer = new StringTokenizer(msg, ";");
        String commandToExcute = stringTokenizer.nextToken();

        switch (commandToExcute) {
            case ServerQueries.CLOSE_NORMALLY:
                System.out.println("Client " + ServerQueries.CLOSE_NORMALLY);
                playerMgr.logOut(currentPlayer.getUsername(), currentPlayer.getStatus());
                currentPlayer.setStatus(PlayerStatusValues.CLOSING);
                ps.println(ServerQueries.CLOSE_NORMALLY);
                closeStream();
                break;
            case ServerQueries.SIGN_UP:
                registerNewPlayer(stringTokenizer.nextToken(), stringTokenizer.nextToken());
                break;
            case ServerQueries.LOGIN:
                playerLogin(stringTokenizer.nextToken(), stringTokenizer.nextToken());
                break;
            case ServerQueries.GET_ONLINE_USERS:
                getOnlineUsers();
                break;
            case ServerQueries.REQUEST_GAME:
                handleRequest();
                break;   
            case ServerQueries.ACCEPT_GAME:
                // sent msg to these 2 players to open the game
                acceptGame(stringTokenizer.nextToken());
                break;
            case ServerQueries.REJECT_GAME:
                // send msg to the sender to notify him with rejection
                rejectGame(stringTokenizer.nextToken());
                break;
            case ServerQueries.START_GAME:
                startGame(stringTokenizer.nextToken());
                break;
            case ServerQueries.TRANSACTION:
                handleTransaction();
                break;
            case ServerQueries.PLAY_AGAIN:
                handlePlayAgainRequest();
                break;
            default:
                System.out.println("UNEXPECTED ERROR MSG: " + msg);
        }
    }

    private void getOnlineUsers() {
        List<PlayerDto> onlinePlayers = playerMgr.getAvilableOnlinePlayersWithScores();
        //remove my name from the online list in order not to send to himself a request  
        System.out.println(currentPlayer.getUsername());
        onlinePlayers.remove(currentPlayer);
        System.out.println(onlinePlayers);
        //Should never happen!
        for(Game game : currentGames){
            onlinePlayers.remove(game.getPlayerX());
            onlinePlayers.remove(game.getPlayerO());
        }
        StringBuilder onlinePlayersString = new StringBuilder("");
        onlinePlayers.forEach((user) -> {
            onlinePlayersString.append(";").append(user.getUsername()).append("~").append(user.getScore());
        });
        //replace the string builder with string if failed
        ps.println(onlinePlayersString.insert(0,ServerQueries.ONLINE_USERS));
    }
    //nexr Token has reciever user name
    private void handleRequest() {
        //case reciver is in game ==> return user busy
        //case user is offline
        //case idle user online
        String recieverUserName = stringTokenizer.nextToken();
        PlayerDto player = playerMgr.getPlayer(recieverUserName);
        if(player.isIsOnline()){
           switch(player.getStatus()){
                case PlayerStatusValues.IDLE:
                    sendRequestToPlayer(recieverUserName);
                    break;
                case PlayerStatusValues.IN_GAME:
                    ps.println(ServerQueries.PLAYER_IS_ALREADY_IN_GAME);
                    break;
                case PlayerStatusValues.WAITING:
                    ps.println(ServerQueries.PLAYER_IS_ALREADY_WAITING_FOR_ANOTHER_GAME);
                    break;
            } 
        }else{
            ps.println(ServerQueries.PLAYER_IS_OFFLINE);
        }
    }
    
    public static void sendMsgToAllUsers(String msg) {
        
    }
    
    public static void onClosingServer() {
        new Thread(() -> {
            PlayerManagerImpl.getInstance(ConnectionDB.getInstance()).updateSetPlayerOffline();
            PlayerManagerImpl.getInstance(ConnectionDB.getInstance()).updateSetPlayerStatusZero();
            PlayerManagerImpl.getInstance(ConnectionDB.getInstance()).updateServerStatistics();
            closeAllResourses();
            
        }).start();
//        sendMsgToAllUsers(msg);
    }
    
    public static void closeAllResourses() {
        for(Map.Entry<String, PlayerDto> session : sessions.entrySet()) {
            System.out.println("session"+session);
            session.getValue().getHandler().releasePlayersResources();
        }
        sessions.clear();
        for(GameHandler game : GameHandler.inActiveSession) {
            System.out.println("Game   "+game);
            game.releasePlayersResources();
        }
        inActiveSession.clear();
        
        // close DB
    }
    
    @Override
    public boolean equals(Object obj) {
        boolean returnValue = false;
        if (obj instanceof GameHandler && id.equals(((GameHandler) obj).id)) {
            returnValue = true;
        }
        
        return returnValue;
    }
    //Ket , Value ==> how to get value from in map ==> map.get(key) ==>
    //key ==> username => 
    //client1 => server  request
    //client2 <= server  recieve msg
    //client2 => server  response
    //client1 <= server  recieve respose
    private void sendRequestToPlayer(String usernameForPersonWhoReceiveTheRequest) {
        new Thread(() -> {
            playerMgr.updatePlayerStatusOnDB(currentPlayer.getUsername(), PlayerStatusValues.WAITING, currentPlayer.getStatus());
            currentPlayer.setStatus(PlayerStatusValues.WAITING);
        }).start();
        sessions
                .get(usernameForPersonWhoReceiveTheRequest)
                .getHandler()
                .ps
                .println(ServerQueries
                        .REQUEST_GAME_FROM
                        .concat(";")
                        .concat(currentPlayer
                                .getUsername()));
    }

    private void rejectGame(String usernameForPersonWhoSentTheRequest) {
        new Thread(() -> {
            playerMgr.updatePlayerStatusOnDB(currentPlayer.getUsername(), PlayerStatusValues.IDLE, currentPlayer.getStatus());
            currentPlayer.setStatus(PlayerStatusValues.IDLE);
        }).start();
        
        sessions
                .get(usernameForPersonWhoSentTheRequest)
                .getHandler()
                .ps
                .println(ServerQueries
                        .REJECT_GAME
                        .concat(";")
                        .concat(currentPlayer
                                .getUsername()));
    }

    private void acceptGame(String usernameForPersonWhoSentTheRequest) {
        new Thread(() -> {
            playerMgr.updatePlayerStatusOnDB(currentPlayer.getUsername(), PlayerStatusValues.WAITING, currentPlayer.getStatus());
            currentPlayer.setStatus(PlayerStatusValues.WAITING);
        }).start();
        
        sessions
                .get(usernameForPersonWhoSentTheRequest)
                .getHandler()
                .ps
                .println(ServerQueries
                        .ACCEPT_GAME
                        .concat(";")
                        .concat(currentPlayer
                                .getUsername()));
    }

    private void startGame(String secondPlayer) {
        Game game = new Game(currentPlayer, sessions.get(secondPlayer));
        currentGames.add(game);
        ps.println(ServerQueries.START_GAME.concat(";").concat(secondPlayer).concat(";").concat(Role.O));
        game
                .getPlayerO()
                .getHandler()
                .ps
                .println(ServerQueries.START_GAME.concat(";").concat(currentPlayer.getUsername()).concat(";").concat(Role.X));
        
        new Thread(() -> {
            playerMgr.updatePlayerStatusOnDB(currentPlayer.getUsername(), PlayerStatusValues.IN_GAME, currentPlayer.getStatus());
            currentPlayer.setStatus(PlayerStatusValues.IN_GAME);
            playerMgr.updatePlayerStatusOnDB(secondPlayer, PlayerStatusValues.IN_GAME, game.getPlayerO().getStatus());
            game.getPlayerO().setStatus(PlayerStatusValues.IN_GAME);
        }).start();
    }

    public static void onAvailablePlayersChangee(List<PlayerDto> avilableOnlinePlayersWithScores) {
        StringBuilder onlinePlayersString = new StringBuilder("");
        avilableOnlinePlayersWithScores.forEach((user) -> {
            onlinePlayersString.append(";").append(user.getUsername()).append("~").append(user.getScore());
        });
        onlinePlayersString.insert(0,ServerQueries.ONLINE_USERS);
        avilableOnlinePlayersWithScores.forEach((player) -> {
            sessions
                    .get(player.getUsername())
                    .getHandler()
                    .ps
                    .println(onlinePlayersString);
        });
    }

    private void handleTransaction() {
        Game temp = getMyGame();
        int step = Integer.parseInt(stringTokenizer.nextToken());
        String role = stringTokenizer.nextToken();
        String transaction = "".concat(""+step).concat(";").concat(role);
        int result = addStepForPlayer(temp, role, step);
        
        checkResult(temp , transaction, result);
    }

    private int addStepForPlayer(Game temp,String role, int step) {
        if(role.equals(Role.X)){
            return temp.addPlayerXStep(step);
        }else{
            return  temp.addPlayerOStep(step);
        }
    }

    private void checkResult(Game temp, String transaction, int result) {//transaction;1;X // 2;O
        switch(result){
            case GameResult.O_WIN:
                sendGameTransaction(temp, ServerQueries.O_WIN.concat(";").concat(transaction));
                break;
            case GameResult.X_WIN:
                sendGameTransaction(temp, ServerQueries.X_WIN.concat(";").concat(transaction));
                break;
            case GameResult.TIE:
                sendGameTransaction(temp, ServerQueries.TIE.concat(";").concat(transaction));
                break;
            case GameResult.CONTINUE_PLAYING:
                 sendGameTransaction(temp,ServerQueries.TRANSACTION.concat(";").concat(transaction));
                break;
        }
    }

    private void sendGameTransaction(Game temp, String query) {
        temp.getPlayerX().getHandler().ps.println(query);
        temp.getPlayerO().getHandler().ps.println(query);
    }

    private void handlePlayAgainRequest() {
        Game temp = getMyGame();
        if(temp.getState() == 1){
            temp.clearPlayersSteps();
            temp
                .getPlayerX()
                .getHandler()
                .ps
                .println(ServerQueries.PLAY_AGAIN);
            temp
                .getPlayerO()
                .getHandler()
                .ps
                .println(ServerQueries.PLAY_AGAIN);
            temp.setState(0);
        }
        else
           temp.setState(1);
    }
    private Game getMyGame(){
        Game temp = new Game(currentPlayer, currentPlayer);
        for (Game g : currentGames) {
            if (g.equals(temp)) {
                temp = g;
                break;
            }
        }
        return temp;
    }
}

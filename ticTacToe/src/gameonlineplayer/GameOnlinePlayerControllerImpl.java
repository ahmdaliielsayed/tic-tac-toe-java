/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gameonlineplayer;

import java.util.StringTokenizer;
import java.util.Vector;
import javafx.stage.Stage;
import tictactoe.Navigation;
import tictactoe.network.NetworkLayer;
import tictactoe.network.NetworkLayerImpl;
import tictactoe.network.NetworkUser;
import utils.Role;
import utils.ServerQueries;

/**
 *
 * @author AhmedAli
 */
interface GameOnlinePlayerController {

    void onBackButtonPressed(Stage stage);
    void onTakeStep(String step, String btnText);
}

public class GameOnlinePlayerControllerImpl implements GameOnlinePlayerController, NetworkUser {

    GameOnlinePlayersScreenInterface gameOnlinePlayersScreenInterface;
    Stage stage;
    Stage mDialog = null;
    NetworkLayer networkLayer;
    private StringTokenizer stringTokenizer;
    
    Vector<Integer> playerMoves;
    String myRole = "";
    
    public GameOnlinePlayerControllerImpl(GameOnlinePlayersScreenInterface gameOnlinePlayersScreenInterface, Stage stage, String secondPlayerName, String secondPlayerRole) {
        this.gameOnlinePlayersScreenInterface = gameOnlinePlayersScreenInterface;
        this.stage = stage;
        networkLayer = NetworkLayerImpl.getInstance(this);
        myRole = secondPlayerRole.equals(Role.X) ? Role.O : Role.X;
        gameOnlinePlayersScreenInterface.displayPlayersData(networkLayer.getUsername(), myRole, secondPlayerName, secondPlayerRole);
        
        playerMoves = new Vector<>();
    }
    
    @Override
    public void onBackButtonPressed(Stage stage) {
        // show confirmation dialog
        Navigation.navigateToOnlinePlayersScreen(stage);
    }

    @Override
    public void exitNetwork(String msg) {
        
    }

    @Override
    public void onMsgReceived(String receivedMsg) {
        stringTokenizer = new StringTokenizer(receivedMsg, ";");
        String commandToExcute = stringTokenizer.nextToken();
        System.out.println(networkLayer.getUsername() + "       " + receivedMsg);
        switch(commandToExcute){
            case ServerQueries.TRANSACTION:
                handleTransaction();
                break;
            case ServerQueries.X_WIN:
                if(myRole.equals(Role.X))
                    handleWin();
                else
                    handleLoose();
                break;
            case ServerQueries.O_WIN:
                if(myRole.equals(Role.O))
                    handleWin();
                else
                    handleLoose();
                break;
            case ServerQueries.TIE:
                handleTie();
                break;
                
        }
    }

    @Override
    public void onTakeStep(String step, String btnText) {
        if (isValidStep(btnText)) {
            networkLayer.printStream(ServerQueries.TRANSACTION.concat(";").concat(step).concat(";").concat(myRole));
        }
    }
    
    // x == 0
    // o == 1
    private boolean isMyTurnToPlay() {
        System.out.println(networkLayer.getUsername()+"     "+myRole+"            "+playerMoves.size());
        return ((myRole.equals(Role.X) && playerMoves.size() % 2 == 0) || (myRole.equals(Role.O) && playerMoves.size() % 2 != 0));
    }
    
    private boolean isValidStep(String btnText){
        return isMyTurnToPlay() && btnText.isEmpty();
    }

    private void handleTransaction() {
        String step = stringTokenizer.nextToken();
        String role = stringTokenizer.nextToken();
        playerMoves.add(Integer.parseInt(step));
        gameOnlinePlayersScreenInterface.displayStepOnBtn(role, step);
    }

    private void handleWin() {
        handleTransaction();
        System.out.println("handleWin");
    }

    private void handleLoose() {
        handleTransaction();
        System.out.println("handleLoose");
    }

    private void handleTie() {
        handleTransaction();
        System.out.println("handleTie");
    }
}
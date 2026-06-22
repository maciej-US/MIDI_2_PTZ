import uci.bcd2ptz.MidiOutputController;
//import uci.bcd2ptz.MidiState;

public class ControllerLogic {

    //private MidiState mode = MidiState.IDLE;
    private boolean acceptingPanTilt = false;

    //private final MidiState state;
//    private final MidiOutputController output;

//    public ControllerLogic(MidiState state, MidiOutputController output) {
//        this.state = state;
//        this.output = output;
//    }

//    public void startWaitForNeutral() {
//
//        //mode = ModeState.WAIT_NEUTRAL;
//        acceptingPanTilt = false;
//
//        System.out.println("WAITING FOR NEUTRAL");
//
//        //output.stateRequest();
//
//        // opcjonalnie szybki check (jeśli już było)
//        checkTilt();
//    }

//    public void onControlChange(int cc, int value) {
//
//        if (mode == ModeState.WAIT_NEUTRAL && cc == 11) {
//            checkTilt();
//        }
//    }

//    private void checkTilt() {
//       // int tilt = state.getCC(11);
//
//        if (tilt == 64) {
//            acceptingPanTilt = true;
//            System.out.println("TILT neutral detected → enabling PanTilt");
//        }
//    }

    public void onNote(String msg) {
        // obsługa NOTE
    }
}
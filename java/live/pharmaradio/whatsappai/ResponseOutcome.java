package live.pharmaradio.whatsappai;
enum ResponseOutcome {
    COMPLETE,CANCELLED,LIMITED,FAILED;
    static ResponseOutcome classify(String state,String reason,boolean continuous){
        if("completed".equals(state))return COMPLETE;
        if(continuous&&"cancelled".equals(state))return CANCELLED;
        if(continuous&&"incomplete".equals(state)&&"max_output_tokens".equals(reason))return LIMITED;
        return FAILED;
    }
}

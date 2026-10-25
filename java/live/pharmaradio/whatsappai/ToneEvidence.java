package live.pharmaradio.whatsappai;
/** Half-second frequency evidence, bounded scalar state, no retained audio. */
final class ToneEvidence {
    final int size; final double omega;
    int count,windows,hits; double real,imag,energy,lastFraction,lastRms;
    ToneEvidence(int rate,int frequency) { size=rate/2; omega=2*Math.PI*frequency/rate; }
    void add(short sample) {
        real+=sample*Math.cos(omega*count); imag+=sample*Math.sin(omega*count);
        energy+=(double)sample*sample;
        if(++count==size) {
            lastRms=Math.sqrt(energy/size);
            lastFraction=energy==0?0:2*(real*real+imag*imag)/(size*energy);
            windows++; if(lastRms>200 && lastFraction>0.65) hits++;
            count=0; real=imag=energy=0;
        }
    }
}

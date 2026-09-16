package com.thunder.wildernessodysseyapi.tools.structureviewer.render;

/** Manual quality presets change pixel resolution, never omit blocks or simplify their geometry. */
public enum RenderQuality {
    FAST("Fast",800,600,1), BALANCED("Balanced",1400,1000,1),
    HIGH("High",2560,1440,1), ULTRA("Ultra · 2×",3840,2160,2);
    private final String label;
    private final int maxWidth,maxHeight;
    private final double multiplier;
    RenderQuality(String label,int maxWidth,int maxHeight,double multiplier){
        this.label=label;this.maxWidth=maxWidth;this.maxHeight=maxHeight;this.multiplier=multiplier;
    }
    /** Uniform scale retains the aspect ratio and accurate screen-space picking. */
    public double scale(int width,int height){return Math.min(multiplier,Math.min((double)maxWidth/Math.max(1,width),(double)maxHeight/Math.max(1,height)));}
    @Override public String toString(){return label;}
}

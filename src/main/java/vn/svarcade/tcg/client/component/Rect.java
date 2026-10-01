package vn.svarcade.tcg.client.component;
public record Rect(int x,int y,int w,int h){public int right(){return x+w;}public int bottom(){return y+h;}public boolean contains(double x,double y){return x>=this.x&&x<right()&&y>=this.y&&y<bottom();}public Rect inset(int n){return new Rect(x+n,y+n,w-2*n,h-2*n);}}

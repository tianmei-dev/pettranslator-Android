Add-Type -AssemblyName System.Drawing
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @"
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;
public static class AssetAudit {
 public static string Inspect(string path) {
  using(var b = new Bitmap(path)) {
   var data=b.LockBits(new Rectangle(0,0,b.Width,b.Height),ImageLockMode.ReadOnly,PixelFormat.Format32bppArgb);
   var bytes=new byte[Math.Abs(data.Stride)*b.Height]; Marshal.Copy(data.Scan0,bytes,0,bytes.Length); b.UnlockBits(data);
   long clear=0, partial=0, opaque=0; int left=b.Width,top=b.Height,right=-1,bottom=-1; long[] edges=new long[4];
   for(int y=0;y<b.Height;y++) for(int x=0;x<b.Width;x++) {
    int a=bytes[y*Math.Abs(data.Stride)+x*4+3];
    if(a==0)clear++; else {if(a==255)opaque++;else partial++; left=Math.Min(left,x);top=Math.Min(top,y);right=Math.Max(right,x);bottom=Math.Max(bottom,y);
     if(y==0)edges[0]++;if(y==b.Height-1)edges[1]++;if(x==0)edges[2]++;if(x==b.Width-1)edges[3]++;
    }
   }
   return String.Format("{0}: {1}x{2}, {3}; transparent={4}, partial={5}, opaque={6}; alpha bbox=({7},{8})-({9},{10}); nonzero edge pixels T/B/L/R={11}/{12}/{13}/{14}",System.IO.Path.GetFileName(path),b.Width,b.Height,b.PixelFormat,clear,partial,opaque,left,top,right,bottom,edges[0],edges[1],edges[2],edges[3]);
  }
 }
}
"@
Get-ChildItem "$PSScriptRoot/../app/src/main/res/drawable-nodpi/*.png" | ForEach-Object { [AssetAudit]::Inspect($_.FullName) }

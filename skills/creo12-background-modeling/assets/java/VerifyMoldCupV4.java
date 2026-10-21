import com.ptc.pfc.pfcSession.*;
import com.ptc.pfc.pfcAssembly.Assembly;
import com.ptc.pfc.pfcSolid.*;
import com.ptc.pfc.pfcModel.*;
import com.ptc.pfc.pfcModelItem.*;
import com.ptc.pfc.pfcComponentFeat.*;
import com.ptc.pfc.pfcBase.*;
import com.ptc.pfc.pfcInterference.*;
import java.io.*;
import java.util.*;

public class VerifyMoldCupV4 {
 public static void start() {
  try(PrintWriter out=new PrintWriter(new FileWriter(System.getenv("CREO_MODEL_OUTPUT")+"\\verification.txt"))) {
   try {
    Session s=pfcSession.GetCurrentSessionWithCompatibility(CreoCompatibility.C4Compatible);
    s.SetConfigOption("regen_failure_handling","resolve_mode");s.ChangeDirectory(System.getenv("CREO_MODEL_OUTPUT"));
    Assembly a=(Assembly)s.RetrieveModel(pfcModel.ModelDescriptor_CreateFromFileName("dundun_cup_mold_v4_final.asm"));
    Map<String,String[]> placements=new HashMap<String,String[]>();
    try(BufferedReader reader=new BufferedReader(new FileReader(System.getenv("CREO_MODEL_OUTPUT")+"\\mesh-placements.csv"))) {
     String line;while((line=reader.readLine())!=null){String[] fields=line.split(",");placements.put(fields[0],fields);}
    }
    a.Regenerate(null);ModelItems items=a.ListItems(ModelItemType.ITEM_FEATURE);int count=0;int shells=0,drafts=0;
    for(int i=0;i<items.getarraysize();i++)if(items.get(i) instanceof ComponentFeat) {
     ComponentFeat c=(ComponentFeat)items.get(i);count++;
     Solid p=(Solid)s.RetrieveModel(c.GetModelDescr());
     if(MoldCupV4.volume(p)<=0||c.GetIsPackaged()||c.GetIsUnderconstrained())throw new Exception("Invalid component "+p.GetFileName());
     out.println("PART "+p.GetFileName()+" volume="+MoldCupV4.volume(p)+" fixed="+(!c.GetIsPackaged()&&!c.GetIsUnderconstrained()));
     com.ptc.pfc.pfcModelItem.ModelItems features=p.ListItems(ModelItemType.ITEM_FEATURE);
     for(int fi=0;fi<features.getarraysize();fi++){
      com.ptc.pfc.pfcFeature.Feature feature=(com.ptc.pfc.pfcFeature.Feature)features.get(fi);
      String type=feature.GetFeatTypeName();out.println("FEATURE "+p.GetFileName()+" "+feature.GetId()+" "+type);
      if(type.toLowerCase().contains("shell"))shells++;
      if(type.toLowerCase().contains("draft"))drafts++;
     }
     Outline3D b=p.EvalOutline(null,null);Matrix3D t=c.GetPosition().GetMatrix();
     String[] placement=placements.get(p.GetFileName());
     if(placement==null)throw new Exception("Missing expected placement "+p.GetFileName());
     Matrix3D expected=MoldCupV4.transform(Boolean.parseBoolean(placement[1]),Double.parseDouble(placement[2]),Double.parseDouble(placement[3]),Double.parseDouble(placement[4])).GetMatrix();
     for(int row=0;row<4;row++)for(int col=0;col<3;col++)if(Math.abs(t.get(row,col)-expected.get(row,col))>1e-6)throw new Exception("Placement mismatch "+p.GetFileName());
     if(p.GetFileName().contains("exact_body")&&Math.abs(MoldCupV4.volume(p)-67709.6992652696)>0.01)throw new Exception("Body volume changed");
     for(int k=0;k<2;k++)out.println("BOUND "+b.get(k).get(0)+","+b.get(k).get(1)+","+b.get(k).get(2));
     for(int k=0;k<4;k++)out.println("TRANSFORM "+t.get(k,0)+","+t.get(k,1)+","+t.get(k,2));out.flush();
     STLBinaryExportInstructions stl=pfcModel.STLBinaryExportInstructions_Create(null);
     stl.SetQuality(null);stl.SetMaxChordHeight(0.01);stl.SetAngleControl(0.8);
     p.Export(p.GetFileName().replace(".prt",".stl"),stl);
    }
    GlobalInterferences clashes=pfcInterference.CreateGlobalEvaluator(a).ComputeGlobalInterference(true);
    out.println("INTERFERENCES "+(clashes==null?0:clashes.getarraysize()));
    if(clashes!=null)for(int i=0;i<clashes.getarraysize();i++) {
     GlobalInterference clash=clashes.get(i);
     out.println("PAIR "+clash.GetSelParts().GetSel1().GetSelModel().GetFileName()+" / "+clash.GetSelParts().GetSel2().GetSelModel().GetFileName());
     try{out.println("VOLUME "+clash.GetVolume().ComputeVolume());}catch(Throwable e){out.println("VOLUME_UNAVAILABLE "+e);}
    }
    if(count!=11)throw new Exception("Expected 11 components");
    out.println("MOLD_FEATURES shells="+shells+" drafts="+drafts);
    if(shells!=1||drafts!=3)throw new Exception("Expected one shell and three draft features");
    if(clashes!=null&&clashes.getarraysize()>0)throw new Exception("Interferences remain");
    out.println("PASS REOPENED_REGENERATED components="+count+" fixed=11 placements=11 interferences=0");
   }catch(Throwable e){e.printStackTrace(out);}
  }catch(IOException e){e.printStackTrace();}
 }
 public static void stop(){}
}



import com.ptc.pfc.pfcSession.*;
import com.ptc.pfc.pfcPart.Part;
import com.ptc.pfc.pfcAssembly.Assembly;
import com.ptc.pfc.pfcSolid.*;
import com.ptc.pfc.pfcUnits.*;
import com.ptc.pfc.pfcModel.*;
import com.ptc.pfc.pfcComponentFeat.*;
import com.ptc.pfc.pfcBase.*;
import com.ptc.pfc.pfcInterference.*;
import com.ptc.wfc.wfcSolid.WSolid;
import com.ptc.wfc.wfcSession.wfcSession;
import java.io.*;
import java.util.*;

public class MoldCupV4 {
 static final String OUTPUT=System.getenv("CREO_MODEL_OUTPUT");
 static PrintWriter log,manifest;
 static Session session;
 static final List<ComponentFeat> components=new ArrayList<ComponentFeat>();
 static final List<Transform3D> positions=new ArrayList<Transform3D>();
 static void units(Solid part)throws Exception {
  UnitSystems systems=part.ListUnitSystems();
  for(int i=0;i<systems.getarraysize();i++)if(systems.get(i).GetName().contains("(mmNs)")) {
   part.SetPrincipalUnits(systems.get(i),pfcUnits.UnitConversionOptions_Create(UnitDimensionConversion.UNITCONVERT_SAME_DIMS));
   part.SetRelativeAccuracy(0.0001);return;
  }
  throw new Exception("Millimeter system unavailable");
 }
 static double volume(Solid part)throws Exception{return part.GetMassPropertyWithDensity(null,MPDensityUse.MP_DENSITY_USE_ALWAYS,1.0).GetVolume();}
 static Part existing(String name)throws Exception {
  if(new File(OUTPUT,name.toLowerCase()+".prt.1").exists())return (Part)session.RetrieveModel(pfcModel.ModelDescriptor_CreateFromFileName(name+".prt"));
  return null;
 }
 static void save(Part p)throws Exception {
  p.Regenerate(null);double v=volume(p);if(v<=0)throw new Exception("No solid volume "+p.GetFileName());
  p.Save();log.println("PART "+p.GetFileName()+" volume="+v);log.flush();
 }
 static double expected(double[][] points) {
  double moment=0;for(int i=0;i<points.length;i++){double[] a=points[i],b=points[(i+1)%points.length];moment+=(a[0]+b[0])*(a[0]*b[1]-b[0]*a[1]);}
  return Math.abs(Math.PI*moment/3);
 }
 static Part revolve(String name,double[][] points)throws Exception {
  Part p=existing(name);if(p!=null)return p;
  p=session.CreatePart(name);units(p);new NativeExactBody().createBody((WSolid)p,points);
  double exp=expected(points);if(Math.abs(volume(p)-exp)>Math.max(0.01,exp*1e-6))throw new Exception("Profile changed "+name+" expected="+exp+" actual="+volume(p));
  save(p);return p;
 }
 static Part band(String name,double ri,double ro)throws Exception {
  Part p=existing(name);if(p!=null)return p;
  p=session.CreatePart(name);units(p);new NativeMountBand().createMount((WSolid)p,ri,ro);
  double w=7,cut=Math.sqrt(ro*ro-w*w),theta=Math.asin(w/ro);
  double expected=(Math.PI*(ro*ro-ri*ri)-(ro*ro*theta-cut*w)+2*w*(62-cut)-2*(4-Math.PI)-((59.8-(ro>50?56.0:48.5))*7.6-0.72))*12;
  log.println("BAND_EXPECTED "+name+" "+expected+" actual="+volume(p));log.flush();
  if(Math.abs(volume(p)-expected)>0.05)throw new Exception("Mount pocket sketch changed");
  NativeMoldOps.draftSides(p,1.5,log);
  save(p);return p;
 }
 static Transform3D transform(boolean rotate,double x,double y,double z)throws Exception {
  // Use the native revolved body's Y axis as assembly height. This lets the
  // root use default placement without Creo overriding a packaged rotation.
  return pfcBase.Transform3D_Create(rotate?
   wfcSession.CreateMatrix3D(1,0,0,0,0,1,0,0,0,0,1,0,x,z,-y,1):
   wfcSession.CreateMatrix3D(1,0,0,0,0,0,-1,0,0,1,0,0,x,z,-y,1));
 }
 static void add(Assembly assembly,Part p,boolean rotate,double x,double y,double z,String color)throws Exception {
  Transform3D placement=transform(rotate,x,y,z);
  ComponentFeat component=(ComponentFeat)assembly.AssembleComponent(p,placement);components.add(component);positions.add(placement);
  manifest.println(p.GetFileName()+","+rotate+","+x+","+y+","+z+","+color);manifest.flush();
  try {
   STLBinaryExportInstructions stl=pfcModel.STLBinaryExportInstructions_Create(null);
   stl.SetQuality(null);stl.SetMaxChordHeight(0.03);stl.SetAngleControl(0.8);
   p.Export(p.GetFileName().replace(".prt",".stl"),stl);
   log.println("STL_EXPORTED "+p.GetFileName());
  }catch(Throwable e){log.println("STL_EXPORT_FAILED "+p.GetFileName()+" "+e);}
  log.flush();
 }
 static void addRevolved(Assembly a,Part p,double minZ,String color)throws Exception {
  Outline3D b=p.EvalOutline(null,null);
  add(a,p,true,-(b.get(0).get(0)+b.get(1).get(0))/2,0,minZ-b.get(0).get(1),color);
 }
 static void addBand(Assembly a,Part p,double radius,double minZ)throws Exception {
  double[] center=NativeMoldOps.axisCenter(p);
  add(a,p,false,-center[0],-center[1],minZ,"30434a");
 }
 static void fix(ComponentFeat c)throws Exception {
  ComponentConstraint constraint=pfcComponentFeat.ComponentConstraint_Create(c==components.get(0)?ComponentConstraintType.ASM_CONSTRAINT_DEF_PLACEMENT:ComponentConstraintType.ASM_CONSTRAINT_FIX);
  constraint.SetAttributes(pfcComponentFeat.ConstraintAttributes_Create(false,false));
  ComponentConstraints constraints=ComponentConstraints.create();constraints.append(constraint);c.SetConstraints(constraints,null);
  if(c.GetIsPackaged()||c.GetIsUnderconstrained())throw new Exception("Not fixed "+c.GetId());
 }
 public static void start() {
  try(PrintWriter out=new PrintWriter(new FileWriter(OUTPUT+"\\build-log.txt"));
      PrintWriter meshes=new PrintWriter(new FileWriter(OUTPUT+"\\mesh-placements.csv"))) {
   log=out;manifest=meshes;
   try {
    session=pfcSession.GetCurrentSessionWithCompatibility(CreoCompatibility.C4Compatible);
    session.SetConfigOption("regen_failure_handling","resolve_mode");session.ChangeDirectory(OUTPUT);
    Part body=(Part)session.RetrieveModel(pfcModel.ModelDescriptor_CreateFromFileName("dundun_exact_body.prt"));
    double[][] lidProfile={{7.5,232},{46.25,232},{46.45,225.5},{48.25,225.5},{48.5,232},{48.6,232},{48.63,233},{51.87,233},{51.9,232},{53,232},{53.2,226.5},{55.3,226.5},{54.90,234.2},{54.48,234.6},{7.7,234.6},{7.42,234.3}};
    Part lid=revolve("DUNDUN_LID_DRAFT_V4",lidProfile);
    Part seal=existing("DUNDUN_O_RING_V3");
    if(seal==null){seal=session.CreatePart("DUNDUN_O_RING_V3");units(seal);new NativeSealTorus().createBody((WSolid)seal,50.25,1.5);double target=2*Math.PI*Math.PI*50.25*1.5*1.5;if(Math.abs(volume(seal)-target)>0.01)throw new Exception("Torus mismatch");save(seal);}
    Part grommet=revolve("DUNDUN_STRAW_GROMMET_V4",new double[][]{{5.15,231.5},{6.85,231.5},{7.18,232},{7.25,234.6},{8.7,234.6},{9.2,235.1},{9.2,235.8},{8.7,236.3},{5.15,236.3}});
    Part straw=revolve("DUNDUN_STRAW_CHAMFER_V3",new double[][]{{4.3,0},{4.7,0},{5,.3},{5,254.7},{4.7,255},{4.3,255},{4,254.7},{4,.3}});
    Part foot=revolve("DUNDUN_FOOT_DRAFT_V4",new double[][]{{0,-4},{17,-4},{17,-5},{20,-5},{20.02,-4.4},{22.98,-4.4},{23,-5},{30,-5},{30.02,-4.4},{32.98,-4.4},{33,-5},{36.5,-5},{38.9,-3.5},{39.3,-2.5},{40,9},{38.4,9},{37.6,0},{0,0}});
    Part lowerBand=band("DUNDUN_LOWER_MOUNT_V4B",44.27,46.2);
    Part upperBand=band("DUNDUN_UPPER_MOUNT_V4B",53.12,55);
    Part lowerLiner=revolve("DUNDUN_LOWER_LINER_V3",new double[][]{{42.8,70},{43.9,70},{43.9,82},{43.7,82}});
    Part upperLiner=revolve("DUNDUN_UPPER_LINER_V3",new double[][]{{52.4,198},{52.75,198},{52.75,210},{52.55,210},{52.55,200}});
    Part handle=existing("DUNDUN_HANDLE_SHELL_V4");
    if(handle==null){handle=session.CreatePart("DUNDUN_HANDLE_SHELL_V4");units(handle);new NativeDetailHandle().createHandle((WSolid)handle);log.println("HANDLE_EXPECTED 15832.61056745 actual="+volume(handle));if(Math.abs(volume(handle)-15832.61056745)>0.05)throw new Exception("Handle dimensions changed");NativeMoldOps.shell(handle,2.2,log);NativeMoldOps.draftSides(handle,1.5,log);save(handle);}
    Assembly a=session.CreateAssembly("DUNDUN_CUP_MOLD_V4_FINAL");units(a);
    addRevolved(a,body,0,"5b9295");addRevolved(a,lid,225.5,"30434a");addRevolved(a,seal,230,"d7dbce");
    addRevolved(a,grommet,231.5,"d7dbce");addRevolved(a,straw,20,"c7d0cf");addRevolved(a,foot,-5,"30434a");
    addBand(a,lowerBand,46.2,70);addBand(a,upperBand,55,198);
    addRevolved(a,lowerLiner,70,"b5bdb4");addRevolved(a,upperLiner,198,"b5bdb4");
    Outline3D hb=handle.EvalOutline(null,null);
    add(a,handle,true,62-hb.get(0).get(0),5,72-hb.get(0).get(1),"30434a");
    for(ComponentFeat component:components)try{fix(component);}catch(Throwable e){log.println("DEFER_FIX "+component.GetId()+" "+e);}
    a.Regenerate(null);
    for(ComponentFeat component:components)if(component.GetIsPackaged()||component.GetIsUnderconstrained())fix(component);
    for(int i=0;i<components.size();i++) {
     ComponentFeat component=components.get(i);Matrix3D actual=component.GetPosition().GetMatrix(),target=positions.get(i).GetMatrix();
     double error=0;for(int row=0;row<4;row++)for(int col=0;col<3;col++)error+=Math.abs(actual.get(row,col)-target.get(row,col));
     if(error>1e-6)throw new Exception("Position changed after constraints "+component.GetId());
    }
    a.Regenerate(null);a.Save();
    log.println("ASSEMBLY_SAVED "+a.GetFileName()+" components="+components.size());log.flush();
    GlobalInterferences interferences=pfcInterference.CreateGlobalEvaluator(a).ComputeGlobalInterference(true);
    log.println("INTERFERENCES "+(interferences==null?0:interferences.getarraysize()));
    if(interferences!=null)for(int i=0;i<interferences.getarraysize();i++) {
     GlobalInterference clash=interferences.get(i);
     log.println("PAIR "+clash.GetSelParts().GetSel1().GetSelModel().GetFileName()+" / "+clash.GetSelParts().GetSel2().GetSelModel().GetFileName());log.flush();
     try{log.println("OVERLAP_VOLUME "+clash.GetVolume().ComputeVolume());}catch(Throwable e){log.println("OVERLAP_VOLUME_UNAVAILABLE "+e);}
    }
    log.println("PASS BUILD volume="+volume(a));
   }catch(Throwable e){e.printStackTrace(log);}
  }catch(IOException e){e.printStackTrace();}
 }
 public static void stop(){}
}




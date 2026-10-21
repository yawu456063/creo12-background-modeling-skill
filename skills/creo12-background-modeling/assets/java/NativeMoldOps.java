import com.ptc.pfc.pfcPart.Part;
import com.ptc.pfc.pfcSession.pfcSession;
import com.ptc.pfc.pfcModelItem.*;
import com.ptc.pfc.pfcGeometry.Surface;
import com.ptc.pfc.pfcBase.*;
import com.ptc.pfc.pfcSelect.*;
import com.ptc.pfc.pfcArgument.pfcArgument;
import com.ptc.wfc.wfcElementTree.*;
import com.ptc.wfc.wfcElemIds.wfcElemIds;
import com.ptc.wfc.wfcFeature.*;
import com.ptc.wfc.wfcFeatureInstructions.*;
import com.ptc.wfc.wfcSession.WSession;
import com.ptc.wfc.wfcSolid.WSolid;
import com.ptc.wfc.wfcSolidInstructions.*;
import com.ptc.wfc.wfcCollection.*;
import java.io.PrintWriter;

public class NativeMoldOps {
 public static double[] axisCenter(Part p)throws Exception {
  ModelItems items=p.ListItems(ModelItemType.ITEM_SURFACE);com.ptc.pfc.pfcGeometry.Cone chosen=null;double area=-1;
  for(int i=0;i<items.getarraysize();i++)if(items.get(i) instanceof com.ptc.pfc.pfcGeometry.Cone){
   com.ptc.pfc.pfcGeometry.Cone cone=(com.ptc.pfc.pfcGeometry.Cone)items.get(i);double a=cone.EvalArea();if(a>area){chosen=cone;area=a;}
  }
  if(chosen==null)throw new Exception("No conical datum on drafted band");
  Matrix3D m=chosen.GetCoordSys().GetMatrix();return new double[]{m.get(3,0),m.get(3,1)};
 }
 static Element integer(int id,int value,int level)throws Exception{return wfcElementTree.Element_Create(id,pfcArgument.CreateIntArgValue(value),level);}
 static Element number(int id,double value,int level)throws Exception{return wfcElementTree.Element_Create(id,pfcArgument.CreateDoubleArgValue(value),level);}
 static Element select(int id,ModelItem item,int level)throws Exception {
  Element e=wfcElementTree.Element_Create(id,null,level);Selections ss=Selections.create();ss.append(pfcSelect.CreateModelItemSelection(item,null));e.SetSelections(ss);return e;
 }
 static Surface broadFace(Part p,boolean top)throws Exception {
  ModelItems items=p.ListItems(ModelItemType.ITEM_SURFACE);Surface chosen=null;double best=-1;
  double z=p.EvalOutline(null,null).get(top?1:0).get(2);
  for(int i=0;i<items.getarraysize();i++){
   Surface surface=(Surface)items.get(i);Outline3D b=surface.GetXYZExtents();
   if(Math.abs(b.get(0).get(2)-z)<1e-5&&Math.abs(b.get(1).get(2)-z)<1e-5) {
    double area=surface.EvalArea();if(area>best){best=area;chosen=surface;}
   }
  }
  if(chosen==null)throw new Exception("No broad face "+p.GetFileName());return chosen;
 }
 static WFeature create(Part p,Elements es,String name)throws Exception {
  WSession s=(WSession)pfcSession.GetCurrentSession();ElementTree tree=s.CreateElementTree(es);
  tree.WriteElementTreeToFile(ElementTreeFormat.ELEMTREE_XML,name+".xml");
  FeatCreateOptions opts=FeatCreateOptions.create();opts.append(FeatCreateOption.FEAT_CR_NO_OPTS);
  return ((WSolid)p).WCreateFeature(tree,opts,wfcSolidInstructions.WRegenInstructions_Create());
 }
 public static void shell(Part p,double thickness,PrintWriter out)throws Exception {
  double before=MoldCupV4.volume(p);Surface face=broadFace(p,true);
  Elements es=Elements.create();es.append(wfcElementTree.Element_Create(wfcElemIds.PRO_E_FEATURE_TREE,null,0));
  es.append(integer(wfcElemIds.PRO_E_FEATURE_TYPE,928,1));
  es.append(wfcElementTree.Element_Create(wfcElemIds.PRO_E_BODY,null,1));
  es.append(integer(wfcElemIds.PRO_E_BODY_USE,2,2));
  es.append(select(wfcElemIds.PRO_E_BODY_SELECT,p.GetDefaultBody(),2));
  es.append(select(wfcElemIds.PRO_E_SHELL_SRF,face,1));
  es.append(number(wfcElemIds.PRO_E_SHELL_THICK,thickness,1));es.append(integer(wfcElemIds.PRO_E_SHELL_FLIP,1,1));
  create(p,es,"shell-handle");p.Regenerate(null);
  double after=MoldCupV4.volume(p);if(after>=before||after<=0)throw new Exception("Invalid shell volume");
  out.println("SHELL thickness="+thickness+" removedFace="+face.GetId()+" volumeBefore="+before+" after="+after);out.flush();
 }
 public static void draftSides(Part p,double degrees,PrintWriter out)throws Exception {
  double before=MoldCupV4.volume(p);Surface neutral=broadFace(p,false);
  ModelItems surfaces=p.ListItems(ModelItemType.ITEM_SURFACE);SurfaceCollectionInstructions instructions=SurfaceCollectionInstructions.create();int count=0;
  for(int i=0;i<surfaces.getarraysize();i++) {
   Surface surface=(Surface)surfaces.get(i);Outline3D b=surface.GetXYZExtents();
   if(Math.abs(b.get(0).get(2)-b.get(1).get(2))<1e-5)continue;
   SurfaceCollectionReferences refs=SurfaceCollectionReferences.create();
   refs.append(wfcCollection.SurfaceCollectionReference_Create(SurfaceCollectionRefType.SURFCOLL_REF_SINGLE,pfcSelect.CreateModelItemSelection(surface,null)));
   instructions.append(wfcCollection.SurfaceCollectionInstruction_Create(SurfaceCollectionInstrType.SURFCOLL_SINGLE_SURF,true,refs,null));count++;
  }
  com.ptc.wfc.wfcCollection.Collection collection=wfcCollection.Collection_Create();collection.SetSurfCollection(wfcCollection.SurfaceCollection_Create(instructions));
  Elements es=Elements.create();es.append(wfcElementTree.Element_Create(wfcElemIds.PRO_E_FEATURE_TREE,null,0));
  es.append(integer(wfcElemIds.PRO_E_FEATURE_TYPE,927,1));es.append(integer(wfcElemIds.PRO_E_DRAFT_SPLIT,0,1));
  Element srfs=wfcElementTree.Element_Create(wfcElemIds.PRO_E_STD_SURF_COLLECTION_APPL,null,1);srfs.SetElemCollection(collection);es.append(srfs);
  es.append(wfcElementTree.Element_Create(wfcElemIds.PRO_E_DIRECTION_COMPOUND,null,1));es.append(select(wfcElemIds.PRO_E_DIRECTION_REFERENCE,neutral,2));es.append(integer(wfcElemIds.PRO_E_DIRECTION_FLIP,1,2));
  es.append(integer(wfcElemIds.PRO_E_DRAFT_CONSTANT_OR_VARIABLE,1,1));es.append(integer(wfcElemIds.PRO_E_DRAFT_INCLUDE_TANGENT,0,1));
  es.append(wfcElementTree.Element_Create(wfcElemIds.PRO_E_DRAFT_SIDE_1,null,1));
  es.append(integer(wfcElemIds.PRO_E_DRAFT_NEUTRAL_OBJECT_TYPE_1,1,2));es.append(select(wfcElemIds.PRO_E_DRAFT_NEUTRAL_PLANE_1,neutral,2));
  es.append(integer(wfcElemIds.PRO_E_DRAFT_DEPENDENT_1,0,2));es.append(number(wfcElemIds.PRO_E_DRAFT_ANGLE_1,degrees,2));
  WFeature feature=create(p,es,"draft-"+p.GetFileName());p.Regenerate(null);
  if(MoldCupV4.volume(p)>before) {
   ElementTree tree=feature.GetElementTree(null,FeatureElemTreeExtractOptions.FEAT_EXTRACT_NO_OPTS);Elements updated=tree.ListTreeElements();
   for(int i=0;i<updated.getarraysize();i++)if(updated.get(i).GetId()==wfcElemIds.PRO_E_DRAFT_ANGLE_1)updated.get(i).SetValue(pfcArgument.CreateDoubleArgValue(-degrees));
   FeatCreateOptions opts=FeatCreateOptions.create();opts.append(FeatCreateOption.FEAT_CR_NO_OPTS);
   feature.RedefineFeature(null,((WSession)pfcSession.GetCurrentSession()).CreateElementTree(updated),opts,wfcSolidInstructions.WRegenInstructions_Create());p.Regenerate(null);
  }
  double after=MoldCupV4.volume(p);if(after<=0||after>=before)throw new Exception("Draft did not reduce material");
  out.println("DRAFT "+p.GetFileName()+" magnitude="+degrees+" faces="+count+" before="+before+" after="+after);out.flush();
 }
}

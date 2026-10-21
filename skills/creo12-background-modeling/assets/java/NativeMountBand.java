import com.ptc.cipjava.jxthrowable;
import com.ptc.pfc.pfcArgument.pfcArgument;
import com.ptc.pfc.pfcSession.pfcSession;

import com.ptc.wfc.wfcElementTree.Element;
import com.ptc.wfc.wfcElementTree.ElementTree;
import com.ptc.wfc.wfcElementTree.Elements;
import com.ptc.wfc.wfcElementTree.SpecialValue;
import com.ptc.wfc.wfcElementTree.wfcElementTree;
import com.ptc.wfc.wfcElemIds.wfcElemIds;
import com.ptc.wfc.wfcFeature.FeatureElemTreeExtractOptions;
import com.ptc.wfc.wfcFeature.WFeature;
import com.ptc.wfc.wfcFeatureInstructions.FeatCreateOption;
import com.ptc.wfc.wfcFeatureInstructions.FeatCreateOptions;
import com.ptc.wfc.wfcSection.Section;


import com.ptc.wfc.wfcSection.wfcSection;
import com.ptc.wfc.wfcSession.WSession;
import com.ptc.wfc.wfcSession.wfcSession;
import com.ptc.wfc.wfcSolid.WSolid;
import com.ptc.wfc.wfcSolidInstructions.WRegenInstructions;
import com.ptc.wfc.wfcSolidInstructions.wfcSolidInstructions;
public class NativeMountBand {

 private static final double LENGTH_MM=12.0;
 private void line(Section s,double x,double y,double u,double v) throws Exception {
  s.AddEntity(wfcSection.SectionEntityLine_Create(wfcSession.CreateOutline2D(x,y,u,v)));
 }
 private void arc(Section s,double x,double y,double a,double b,double r) throws Exception {
  s.AddEntity(wfcSection.SectionEntityArc_Create(wfcSession.CreatePoint2D(x,y),a,b,r));
 }
    public void createMount(WSolid solid,double innerRadius,double outerRadius) throws Exception, jxthrowable {
        WSession session = (WSession) pfcSession.GetCurrentSession();
        Elements elements = Elements.create();

        // Values are from the locally installed Creo 12 ProFeatType.h and
        // ProExtrude.h: first feature, extrude, blind depth on side one.
        elements.append(wfcElementTree.Element_Create(wfcElemIds.PRO_E_FEATURE_TREE, null, 0));
        elements.append(wfcElementTree.Element_Create(wfcElemIds.PRO_E_FEATURE_TYPE,
                pfcArgument.CreateIntArgValue(0), 1));
        elements.append(wfcElementTree.Element_Create(wfcElemIds.PRO_E_FEATURE_FORM,
                pfcArgument.CreateIntArgValue(1), 1));
        elements.append(wfcElementTree.Element_Create(wfcElemIds.PRO_E_STD_EXT_DEPTH, null, 1));
        elements.append(wfcElementTree.Element_Create(wfcElemIds.PRO_E_EXT_DEPTH_FROM, null, 2));
        elements.append(wfcElementTree.Element_Create(wfcElemIds.PRO_E_EXT_DEPTH_FROM_TYPE,
                pfcArgument.CreateIntArgValue(128), 3));
        elements.append(wfcElementTree.Element_Create(wfcElemIds.PRO_E_EXT_DEPTH_FROM_VALUE,
                pfcArgument.CreateDoubleArgValue(LENGTH_MM), 3));
        elements.append(wfcElementTree.Element_Create(wfcElemIds.PRO_E_EXT_SURF_CUT_SOLID_TYPE, pfcArgument.CreateIntArgValue(917), 1));
        elements.append(wfcElementTree.Element_Create(wfcElemIds.PRO_E_FEAT_FORM_IS_THIN, pfcArgument.CreateIntArgValue(0), 1));
        elements.append(wfcElementTree.Element_Create(wfcElemIds.PRO_E_BODY_USE, pfcArgument.CreateIntArgValue(0), 1));
        ElementTree tree = session.CreateElementTree(elements);
        FeatCreateOptions incomplete = FeatCreateOptions.create();
        incomplete.append(FeatCreateOption.FEAT_CR_INCOMPLETE_FEAT);
        WRegenInstructions regeneration = wfcSolidInstructions.WRegenInstructions_Create();
        WFeature feature = solid.WCreateFeature(tree, incomplete, regeneration);

        tree = feature.GetElementTree(null, FeatureElemTreeExtractOptions.FEAT_EXTRACT_NO_OPTS);
        com.ptc.wfc.wfcElementTree.ElemPathItems pathItems=com.ptc.wfc.wfcElementTree.ElemPathItems.create();
        pathItems.append(wfcElementTree.ElemPathItem_Create(com.ptc.wfc.wfcElementTree.ElemPathItemType.ELEM_PATH_ITEM_TYPE_ID,wfcElemIds.PRO_E_SKETCHER));
        Element sketchElement=tree.GetElement(wfcElementTree.ElementPath_Create(pathItems));
        SpecialValue specialValue = sketchElement.GetSpecialValueElem();
        if (specialValue == null) specialValue = wfcElementTree.SpecialValue_Create();
        Section sketch = specialValue.GetSectionValue();
        if (sketch == null) sketch = session.CreateSection2D();
        sketch.SetIntentManagerMode(false);
        double w=7.0,theta=Math.asin(w/outerRadius);
        double cut=Math.sqrt(outerRadius*outerRadius-w*w);
        arc(sketch,0,0,theta,2*Math.PI-theta,outerRadius);
        line(sketch,cut,-7,60,-7);
        arc(sketch,60,-5,-Math.PI/2,0,2);
        line(sketch,62,-5,62,5);
        arc(sketch,60,5,0,Math.PI/2,2);
        line(sketch,60,7,cut,7);
        sketch.AddEntity(wfcSection.SectionEntityCircle_Create(wfcSession.CreatePoint2D(0,0),innerRadius));
        double x0=outerRadius>50?56.0:48.5;
        double[][] pocket={{x0+0.6,-3.8},{59.2,-3.8},{59.8,-3.2},{59.8,3.2},{59.2,3.8},{x0+0.6,3.8},{x0,3.2},{x0,-3.2}};
        for(int j=0;j<pocket.length;j++){
          double[] a=pocket[j],b=pocket[(j+1)%pocket.length];
          com.ptc.pfc.pfcBase.Point2Ds pts=com.ptc.pfc.pfcBase.Point2Ds.create();
          pts.append(wfcSession.CreatePoint2D(a[0],a[1]));pts.append(wfcSession.CreatePoint2D(b[0],b[1]));
          sketch.AddEntity(wfcSection.SectionEntitySpline_Create(com.ptc.wfc.wfcSection.SplineTangentType.SPLINE_TANGENT_NONE,pts,0,0));
        }
        sketch.SetIntentManagerMode(false);
        sketch.AutoDimension(); sketch.Regenerate();
        specialValue.SetSectionValue(sketch);
        sketchElement.SetSpecialValueElem(specialValue);
        Elements updated=tree.ListTreeElements();
        for(int i=0;i<updated.getarraysize();i++) {
          if(updated.get(i).GetId()==wfcElemIds.PRO_E_SKETCHER && updated.get(i).GetLevel()==1) updated.set(i,sketchElement);
        }
        tree=session.CreateElementTree(updated);

        FeatCreateOptions complete = FeatCreateOptions.create();
        complete.append(FeatCreateOption.FEAT_CR_NO_OPTS);
        tree.WriteElementTreeToFile(com.ptc.wfc.wfcElementTree.ElementTreeFormat.ELEMTREE_XML,"cup-handle-tree.xml");
        feature.RedefineFeature(null, tree, complete, regeneration);
    }

}












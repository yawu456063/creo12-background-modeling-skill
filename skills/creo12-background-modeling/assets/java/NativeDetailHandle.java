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
public class NativeDetailHandle {

 private static final double LENGTH_MM=10.0;
 private void line(Section s,double x,double y,double u,double v) throws Exception {
    com.ptc.pfc.pfcBase.Point2Ds points=com.ptc.pfc.pfcBase.Point2Ds.create();
  points.append(wfcSession.CreatePoint2D(x,y));points.append(wfcSession.CreatePoint2D(u,v));
  s.AddEntity(wfcSection.SectionEntitySpline_Create(com.ptc.wfc.wfcSection.SplineTangentType.SPLINE_TANGENT_NONE,points,0,0));
 }
 private void arc(Section s,double x,double y,double a,double b,double r) throws Exception {
  s.AddEntity(wfcSection.SectionEntityArc_Create(wfcSession.CreatePoint2D(x,y),a,b,r));
 }
    public void createHandle(WSolid solid) throws Exception, jxthrowable {
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
        line(sketch,62,72,79,72);
        arc(sketch,79,80,-Math.PI/2,0,8);
        line(sketch,87,80,87,200);
        arc(sketch,79,200,0,Math.PI/2,8);
        line(sketch,79,208,62,208);
        line(sketch,62,208,62,200);
        line(sketch,62,200,72,200);
        arc(sketch,72,195,0,Math.PI/2,5);
        line(sketch,77,195,77,85);
        arc(sketch,72,85,-Math.PI/2,0,5);
        line(sketch,72,80,62,80);
        line(sketch,62,80,62,72);
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












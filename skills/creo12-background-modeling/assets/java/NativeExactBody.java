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
public class NativeExactBody {


    public void createBody(WSolid solid, double[][] profile) throws Exception, jxthrowable {
        WSession session = (WSession) pfcSession.GetCurrentSession();
        Elements elements = Elements.create();

        // Values are from the locally installed Creo 12 ProFeatType.h and
        // ProExtrude.h: first feature, extrude, blind depth on side one.
        elements.append(wfcElementTree.Element_Create(wfcElemIds.PRO_E_FEATURE_TREE, null, 0));
        elements.append(wfcElementTree.Element_Create(wfcElemIds.PRO_E_FEATURE_TYPE,
                pfcArgument.CreateIntArgValue(0), 1));
        elements.append(wfcElementTree.Element_Create(wfcElemIds.PRO_E_FEATURE_FORM,
                pfcArgument.CreateIntArgValue(2), 1));
        elements.append(wfcElementTree.Element_Create(wfcElemIds.PRO_E_REV_ANGLE, null, 1));
        elements.append(wfcElementTree.Element_Create(wfcElemIds.PRO_E_REV_ANGLE_FROM, null, 2));
        elements.append(wfcElementTree.Element_Create(wfcElemIds.PRO_E_REV_ANGLE_FROM_TYPE,
                pfcArgument.CreateIntArgValue(4096), 3));
        elements.append(wfcElementTree.Element_Create(wfcElemIds.PRO_E_REV_ANGLE_FROM_VAL,
                pfcArgument.CreateDoubleArgValue(360.0), 3));
        elements.append(wfcElementTree.Element_Create(wfcElemIds.PRO_E_EXT_SURF_CUT_SOLID_TYPE, pfcArgument.CreateIntArgValue(917), 1));
        elements.append(wfcElementTree.Element_Create(wfcElemIds.PRO_E_FEAT_FORM_IS_THIN, pfcArgument.CreateIntArgValue(0), 1));
        elements.append(wfcElementTree.Element_Create(wfcElemIds.PRO_E_BODY_USE, pfcArgument.CreateIntArgValue(0), 1));
        elements.append(wfcElementTree.Element_Create(wfcElemIds.PRO_E_REVOLVE_AXIS_OPT,pfcArgument.CreateIntArgValue(1),1));
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
        sketch.SetEpsilon(0.00001);
        sketch.SetIntentManagerMode(false);
        sketch.AddEntity(wfcSection.SectionEntityCenterLine_Create(wfcSession.CreateOutline2D(0,-10,0,250)));
        for(int i=0;i<profile.length;i++) {
          double[] a=profile[i],b=profile[(i+1)%profile.length];
          com.ptc.pfc.pfcBase.Point2Ds points=com.ptc.pfc.pfcBase.Point2Ds.create();
          points.append(wfcSession.CreatePoint2D(a[0],a[1]));
          points.append(wfcSession.CreatePoint2D(b[0],b[1]));
          sketch.AddEntity(wfcSection.SectionEntitySpline_Create(com.ptc.wfc.wfcSection.SplineTangentType.SPLINE_TANGENT_NONE,points,0,0));
        }
        sketch.SetIntentManagerMode(false);
        sketch.AutoDimension();
        sketch.Regenerate();
        specialValue.SetSectionValue(sketch);
        sketchElement.SetSpecialValueElem(specialValue);
        Elements updated=tree.ListTreeElements();
        for(int i=0;i<updated.getarraysize();i++) {
          if(updated.get(i).GetId()==wfcElemIds.PRO_E_SKETCHER && updated.get(i).GetLevel()==1) updated.set(i,sketchElement);
          if(updated.get(i).GetId()==wfcElemIds.PRO_E_REVOLVE_AXIS_OPT) updated.get(i).SetValue(pfcArgument.CreateIntArgValue(0));
          if(updated.get(i).GetId()==wfcElemIds.PRO_E_STD_DIRECTION) updated.get(i).SetValue(pfcArgument.CreateIntArgValue(-1));
        }
        tree=session.CreateElementTree(updated);

        FeatCreateOptions complete = FeatCreateOptions.create();
        complete.append(FeatCreateOption.FEAT_CR_NO_OPTS);
        tree.WriteElementTreeToFile(com.ptc.wfc.wfcElementTree.ElementTreeFormat.ELEMTREE_XML,"cup-body-tree.xml");
        feature.RedefineFeature(null, tree, complete, regeneration);
    }

}











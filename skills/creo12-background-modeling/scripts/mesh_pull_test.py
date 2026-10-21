from pathlib import Path
import numpy as np
import json, sys

def review(path,spacing=.12):
    data=path.read_bytes(); count=int.from_bytes(data[80:84],'little')
    dt=np.dtype([('normal','<f4',(3,)),('v','<f4',(3,3)),('attr','<u2')])
    triangles=np.frombuffer(data,dtype=dt,count=count,offset=84)['v'].astype(float)
    xy_min=triangles[:,:,:2].min(axis=(0,1));xy_max=triangles[:,:,:2].max(axis=(0,1))
    nx,ny=np.ceil((xy_max-xy_min)/spacing).astype(int)
    samples=np.full((ny*nx,32),np.inf,dtype=np.float64);numbers=np.zeros(ny*nx,dtype=np.int32)
    for tri in triangles:
        a,b,c=tri
        det=(a[0]-c[0])*(b[1]-c[1])-(a[1]-c[1])*(b[0]-c[0])
        if abs(det)<1e-11:continue
        lo=np.maximum(0,np.floor((tri[:,:2].min(axis=0)-xy_min)/spacing).astype(int))
        hi=np.minimum([nx-1,ny-1],np.ceil((tri[:,:2].max(axis=0)-xy_min)/spacing).astype(int))
        yy,xx=np.mgrid[lo[1]:hi[1]+1,lo[0]:hi[0]+1]
        px=xy_min[0]+(xx+.413)*spacing;py=xy_min[1]+(yy+.371)*spacing
        w0=((px-c[0])*(b[1]-c[1])-(py-c[1])*(b[0]-c[0]))/det
        w1=((a[0]-c[0])*(py-c[1])-(a[1]-c[1])*(px-c[0]))/det
        w2=1-w0-w1
        mask=(w0>=-1e-8)&(w1>=-1e-8)&(w2>=-1e-8)
        if not mask.any():continue
        ids=(yy[mask]*nx+xx[mask]).ravel();z=(w0[mask]*a[2]+w1[mask]*b[2]+w2[mask]*c[2]).ravel()
        cols=numbers[ids]
        if (cols>=32).any():raise RuntimeError('too many intersections')
        samples[ids,cols]=z;numbers[ids]+=1
    samples.sort(axis=1)
    with np.errstate(invalid='ignore'):
        unique=(np.isfinite(samples[:,1:])&(np.diff(samples,axis=1)>1e-4)).sum(axis=1)+np.isfinite(samples[:,0])
    mult=np.flatnonzero((unique>=4)&(unique%2==0));valid=np.flatnonzero((unique>=2)&(unique%2==0))
    examples=[]
    for idx in mult[:5]:
        values=samples[idx,np.isfinite(samples[idx])]
        values=values[np.r_[True,np.diff(values)>1e-4]]
        examples.append({'x':float(xy_min[0]+(idx%nx+.413)*spacing),'y':float(xy_min[1]+(idx//nx+.371)*spacing),'z_intersections':values.tolist()})
    return {'file':path.name,'spacing_mm':spacing,'valid_rays':len(valid),'multi_interval_rays':len(mult),'max_intersections':int(unique.max()),'examples':examples,'scope':'axial grid screening; not a complete mold release simulation'}

root=Path(sys.argv[1]) if len(sys.argv)>1 else Path.cwd()
results=[review(p) for p in root.glob('*.stl') if any(n in p.name for n in ['handle_shell','lower_mount','upper_mount'])]
(root/'pull-test.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps(results,ensure_ascii=False,indent=2))

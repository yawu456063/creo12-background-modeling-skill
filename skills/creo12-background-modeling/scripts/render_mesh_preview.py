from pathlib import Path
import csv
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = Path(sys.argv[1]) if len(sys.argv)>1 else Path.cwd()
dtype = np.dtype([('n','<f4',(3,)),('v','<f4',(3,3)),('a','<u2')])
all_vertices, all_colors = [], []
for row in csv.reader((ROOT/'mesh-placements.csv').open()):
    name, rotate, sx, sy, sz, color = row
    mesh_root=ROOT/'preview_meshes' if (ROOT/'preview_meshes').exists() else ROOT
    data = (mesh_root/name.replace('.prt','.stl')).read_bytes()
    count = int.from_bytes(data[80:84], 'little')
    triangles = np.frombuffer(data, dtype=dtype, offset=84, count=count)['v'].astype(float)
    if rotate == 'true':
        triangles = triangles[..., [0,2,1]] * np.array([1,-1,1])
    triangles += np.array([float(sx),float(sy),float(sz)])
    all_vertices.append(triangles)
    all_colors.append(np.tile(np.array([int(color[i:i+2],16) for i in (0,2,4)]), (count,1)))
vertices = np.concatenate(all_vertices)
colors = np.concatenate(all_colors)
normals = np.cross(vertices[:,1]-vertices[:,0],vertices[:,2]-vertices[:,0])
normals /= np.maximum(np.linalg.norm(normals,axis=1)[:,None],1e-12)
light = np.array([-.5,-.7,1.2]);light /= np.linalg.norm(light)
shading = .52+.48*np.maximum(0,normals@light)
face_colors = np.clip(colors*shading[:,None]+10,0,255).astype(np.uint8)

S = 2
image = Image.new('RGB',(1600*S,1200*S),'#f4f5f3')
draw = ImageDraw.Draw(image)
font_path = r'C:\Windows\Fonts\msyh.ttc'
font = lambda size: ImageFont.truetype(font_path,size*S)
draw.text((70*S,45*S),'吨吨杯  第四版出模结构细化',font=font(32),fill='#203c43')
draw.text((72*S,97*S),'Creo 12 原生模型导出网格 · 统一竖直方向显示',font=font(16),fill='#697974')

def panel(rect,view,target,span,title,subtitle):
    x0,y0,x1,y1=rect
    layer=Image.new('RGB',((x1-x0)*S,(y1-y0)*S),'#ffffff')
    brush=ImageDraw.Draw(layer)
    eye=np.array(view,dtype=float);eye/=np.linalg.norm(eye)
    right=np.cross(np.array([0,0,1]),eye);right/=np.linalg.norm(right)
    up=np.cross(eye,right)
    centered=vertices-np.array(target)
    scale=(y1-y0-85)*S/span
    px=centered@right*scale+(x1-x0)*S*.5
    py=-centered@up*scale+(y1-y0)*S*.52
    depth=centered@eye
    visible=(normals@eye)>-.02
    projected=np.stack([px,py],axis=-1)
    canvas=np.full((layer.height,layer.width,3),255,dtype=np.uint8)
    zbuffer=np.full((layer.height,layer.width),-np.inf,dtype=np.float32)
    for i in range(len(vertices)):
        if not visible[i]:continue
        pts=projected[i]
        if pts[:,0].max()<0 or pts[:,0].min()>layer.width or pts[:,1].max()<0 or pts[:,1].min()>layer.height:continue
        left=max(0,int(np.floor(pts[:,0].min())))
        right_edge=min(layer.width-1,int(np.ceil(pts[:,0].max())))
        top=max(0,int(np.floor(pts[:,1].min())))
        bottom=min(layer.height-1,int(np.ceil(pts[:,1].max())))
        a,b,c=pts
        determinant=(a[0]-c[0])*(b[1]-c[1])-(a[1]-c[1])*(b[0]-c[0])
        if abs(determinant)<1e-8:continue
        yy,xx=np.ogrid[top:bottom+1,left:right_edge+1]
        w0=((xx+.5-c[0])*(b[1]-c[1])-(yy+.5-c[1])*(b[0]-c[0]))/determinant
        w1=((a[0]-c[0])*(yy+.5-c[1])-(a[1]-c[1])*(xx+.5-c[0]))/determinant
        w2=1-w0-w1
        z=w0*depth[i,0]+w1*depth[i,1]+w2*depth[i,2]
        old=zbuffer[top:bottom+1,left:right_edge+1]
        mask=(w0>=-1e-6)&(w1>=-1e-6)&(w2>=-1e-6)&(z>old)
        old[mask]=z[mask]
        canvas[top:bottom+1,left:right_edge+1][mask]=face_colors[i]
    layer=Image.fromarray(canvas)
    brush=ImageDraw.Draw(layer)
    brush.rectangle((0,0,layer.width,84*S),fill='#ffffff')
    brush.text((26*S,20*S),title,font=font(21),fill='#203c43')
    brush.text((26*S,54*S),subtitle,font=font(14),fill='#697974')
    image.paste(layer,(x0*S,y0*S))

panel((55,155,910,1125),[.65,-1,.4],[15,0,135],310,'整体装配','上下抱箍与衬圈支撑提手  /  底部环形护套')
panel((935,155,1545,660),[.5,-1,.7],[0,0,232],125,'杯盖细节','取消周向倒扣槽  /  薄壁杯盖  /  吸管护圈')
panel((935,685,1545,1125),[.75,-1,.25],[30,0,44],125,'下部连接与底护套','抱箍减胶孔与拔模  /  弹性衬圈及底护套')
draw.text((65*S,1150*S),'结构设计稿：尚未验证夹紧强度、密封性能与制造公差。',font=font(15),fill='#697974')
image.resize((1600,1200),Image.Resampling.LANCZOS).save(ROOT/'模型预览.png')
print('triangles',len(vertices))
print(ROOT/'模型预览.png')

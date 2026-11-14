"""Generate the CupFight car as an editable glTF and a runtime OBJ.

The model uses x across the car, y up, z towards the rear. Build with Python 3.
"""

import json
import math
import struct
from dataclasses import dataclass, field
from functools import lru_cache
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MODEL = "cupfight_car"


@dataclass(frozen=True)
class Material:
    rgb: tuple
    metallic: float
    roughness: float
    emission: tuple = (0, 0, 0)


MATERIALS = {
    "body": Material((0.28, 0.38, 0.50), 0.72, 0.25),
    "roof": Material((0.095, 0.15, 0.21), 0.49, 0.21),
    "glass": Material((0.088, 0.13, 0.18), 0.37, 0.21),
    "glass_trim": Material((0.043, 0.055, 0.071), 0.22, 0.35),
    "seam": Material((0.047, 0.063, 0.081), 0.17, 0.38),
    "tire": Material((0.035, 0.042, 0.050), 0.04, 0.76),
    "wheel": Material((0.20, 0.25, 0.30), 0.76, 0.27),
    "wheel_hub": Material((0.078, 0.099, 0.127), 0.61, 0.36),
    "tail_lens": Material((0.28, 0.035, 0.053), 0.38, 0.24),
    "tail_glow": Material((0.99, 0.12, 0.08), 0.0, 0.25, (1.0, 0.035, 0.012)),
    "rear_reflector": Material((0.48, 0.055, 0.07), 0.26, 0.27),
    "diffuser": Material((0.050, 0.064, 0.082), 0.29, 0.42),
    "bright_trim": Material((0.36, 0.45, 0.55), 0.72, 0.27),
    "headlamp": Material((0.73, 0.85, 0.96), 0.27, 0.21, (0.36, 0.48, 0.65)),
    "front_grille": Material((0.030, 0.042, 0.054), 0.32, 0.40),
    "mirror": Material((0.28, 0.38, 0.47), 0.72, 0.18),
}


def subtract(a, b):
    return tuple(x - y for x, y in zip(a, b))


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def unit(v):
    magnitude = math.sqrt(dot(v, v))
    return tuple(x / magnitude for x in v) if magnitude > 1e-10 else (0, 1, 0)


@dataclass
class Mesh:
    name: str
    material: str
    vertices: list = field(default_factory=list)
    triangles: list = field(default_factory=list)
    indices: dict = field(default_factory=dict)

    def face(self, points, outward=None):
        if outward is not None:
            normal = cross(subtract(points[1], points[0]), subtract(points[2], points[0]))
            if dot(normal, outward) < 0:
                points = list(reversed(points))
        indices = []
        for point in points:
            key = tuple(round(value, 6) for value in point)
            if key not in self.indices:
                self.indices[key] = len(self.vertices)
                self.vertices.append(key)
            indices.append(self.indices[key])
        for i in range(1, len(indices) - 1):
            a, b, c = indices[0], indices[i], indices[i + 1]
            normal = cross(subtract(self.vertices[b], self.vertices[a]),
                           subtract(self.vertices[c], self.vertices[a]))
            if dot(normal, normal) > 1e-15:
                self.triangles.append((a, b, c))

    def normals(self):
        result = [(0, 0, 0) for _ in self.vertices]
        for a, b, c in self.triangles:
            normal = cross(subtract(self.vertices[b], self.vertices[a]),
                           subtract(self.vertices[c], self.vertices[a]))
            for index in (a, b, c):
                result[index] = tuple(x + y for x, y in zip(result[index], normal))
        return [unit(normal) for normal in result]


AXLES = (-1.36, 1.34)
WHEEL_RADIUS = 0.335
ARCH_RADIUS = 0.353
BODY = (
    # z, half-width, center deck, sill height
    (-2.22, 0.858, 0.657, 0.228),
    (-2.12, 0.927, 0.693, 0.194),
    (-1.89, 0.958, 0.744, 0.180),
    (-1.58, 0.980, 0.801, 0.184),
    (-1.29, 0.982, 0.816, 0.183),
    (-0.99, 0.957, 0.829, 0.180),
    (-0.62, 0.928, 0.841, 0.169),
    (-0.06, 0.931, 0.856, 0.163),
    (0.50, 0.965, 0.870, 0.169),
    (0.98, 0.999, 0.878, 0.175),
    (1.29, 1.012, 0.884, 0.181),
    (1.61, 1.010, 0.870, 0.182),
    (1.90, 0.983, 0.846, 0.193),
    (2.10, 0.943, 0.806, 0.206),
    (2.25, 0.904, 0.757, 0.253),
)

CABIN = (
    # z, roof half-width, crown height, belt half-width
    (-1.28, 0.704, 0.831, 0.778),
    (-1.13, 0.665, 0.976, 0.794),
    (-0.91, 0.629, 1.143, 0.797),
    (-0.68, 0.609, 1.286, 0.800),
    (-0.52, 0.608, 1.338, 0.801),
    (-0.26, 0.622, 1.356, 0.809),
    (0.08, 0.638, 1.354, 0.820),
    (0.37, 0.649, 1.325, 0.839),
    (0.54, 0.661, 1.287, 0.854),
    (0.77, 0.680, 1.205, 0.880),
    (1.02, 0.710, 1.086, 0.900),
    (1.29, 0.744, 0.971, 0.910),
    (1.48, 0.769, 0.901, 0.910),
    (1.62, 0.792, 0.884, 0.903),
    (1.71, 0.814, 0.866, 0.898),
)


def interpolate(table, z):
    for a, b in zip(table, table[1:]):
        if z <= b[0]:
            t = max(0, (z - a[0]) / (b[0] - a[0]))
            return tuple(a[i] + (b[i] - a[i]) * t for i in range(1, len(a)))
    return table[-1][1:]


def swept_depth(z, side):
    """Round the bumper corners in plan rather than ending the hull on a flat cut."""
    rear = max(0, (z - 1.72) / 0.53) * 0.143
    front = max(0, (-z - 1.69) / 0.53) * 0.133
    return z + (front - rear) * abs(side) ** 3


def opening_height(z, sill):
    for axle in AXLES:
        delta = abs(z - axle)
        if delta < ARCH_RADIUS:
            return max(sill, WHEEL_RADIUS + math.sqrt(ARCH_RADIUS ** 2 - delta ** 2))
    return sill


def hull_ring(z):
    width, top, sill = interpolate(BODY, z)
    opening = opening_height(z, sill)
    shoulder = top - 0.061
    half = [
        (0.00, top),
        (0.53, top - 0.010),
        (0.80, top - 0.023),
        (0.948, shoulder),
        (1.00, max(opening + 0.010, shoulder * 0.60 + opening * 0.40)),
        (0.973, opening),
        (0.728, min(sill, 0.237)),
        (0.00, min(sill, 0.237)),
    ]
    contour = half + [(-x, y) for x, y in reversed(half[1:-1])]
    return [(width * side, y, swept_depth(z, side)) for side, y in contour]


def make_body(meshes):
    body = Mesh("Steel blue body", "body")
    meshes.append(body)
    depth_rows = {row[0] for row in BODY if all(abs(row[0] - axle) > ARCH_RADIUS for axle in AXLES)}
    for axle in AXLES:
        # Rows at the wheel arch keep the metal opening clear of the tire at every angle.
        depth_rows.update(axle + ARCH_RADIUS * math.cos(math.pi * i / 6) for i in range(1, 6))
        depth_rows.update((axle - 0.355, axle - 0.352, axle + 0.352, axle + 0.355))
    rings = [hull_ring(z) for z in sorted(depth_rows)]
    for near, far in zip(rings, rings[1:]):
        for i in range(len(near)):
            next_i = (i + 1) % len(near)
            body.face([near[i], far[i], far[next_i], near[next_i]])
    for ring, z, outward in ((rings[0], BODY[0][0], (0, 0, -1)),
                             (rings[-1], BODY[-1][0], (0, 0, 1))):
        end = Mesh("Rear fascia" if z > 0 else "Front fascia", "body")
        meshes.append(end)
        for triangle in fascia_section(z > 0):
            end.face(triangle, outward)


def crown(z, side):
    width, height, _ = interpolate(CABIN, z)
    return (width * side, height - 0.036 * side * side, z)


def cabin_side(z, position, sign):
    width, height, belt = interpolate(CABIN, z)
    _, deck, _ = interpolate(BODY, z)
    crown_y = height - 0.036
    belt_y = min(deck - 0.049, crown_y - 0.005)
    return (sign * (width + (belt - width) * position),
            crown_y + (belt_y - crown_y) * position, z)


def make_cabin(meshes):
    metal = Mesh("Roof rails and cabin frame", "body")
    windshield = Mesh("Front windshield", "glass")
    roof = Mesh("Panoramic roof", "roof")
    rear = Mesh("Tapered rear glass", "glass")
    trim = Mesh("Glazing border", "glass_trim")
    meshes.extend((metal, windshield, roof, rear, trim))
    cross_rows = (-1.0, -0.87, -0.81, -0.52, 0, 0.52, 0.81, 0.87, 1.0)
    front_window = (-1.07, -0.095)
    rear_window = (0.015, 1.32)
    depths = sorted({*(row[0] for row in CABIN), *front_window, *rear_window})
    for z1, z2 in zip(depths, depths[1:]):
        mid = (z1 + z2) / 2
        for x1, x2 in zip(cross_rows, cross_rows[1:]):
            inside = max(abs(x1), abs(x2)) <= 0.81
            border = max(abs(x1), abs(x2)) <= 0.87
            panel = metal
            if -1.13 <= mid < 1.62 and border:
                panel = trim
                if inside:
                    if mid < -0.52:
                        panel = windshield
                    elif mid < 0.46:
                        panel = roof
                    elif mid >= 0.54:
                        panel = rear
            panel.face([crown(z1, x1), crown(z2, x1), crown(z2, x2), crown(z1, x2)], (0, 1, 0))
    for sign in (-1, 1):
        side = "Left" if sign < 0 else "Right"
        frame = Mesh(side + " side window frame", "body")
        side_glass = Mesh(side + " side windows", "glass")
        meshes.extend((frame, side_glass))
        for z1, z2 in zip(depths, depths[1:]):
            has_window = any(start <= z1 and z2 <= end for start, end in (front_window, rear_window))
            top1 = 0.29 if z1 in front_window or z1 in rear_window else 0.17
            top2 = 0.29 if z2 in front_window or z2 in rear_window else 0.17
            bands = ((0, top1, 0, top2, frame),
                     (top1, 0.77, top2, 0.77, side_glass if has_window else frame),
                     (0.77, 1, 0.77, 1, frame))
            for start1, end1, start2, end2, part in bands:
                part.face([cabin_side(z1, start1, sign), cabin_side(z2, start2, sign),
                           cabin_side(z2, end2, sign), cabin_side(z1, end1, sign)], (sign, 0, 0))


@lru_cache(maxsize=2)
def fascia_section(rear):
    z = BODY[-1 if rear else 0][0]
    ring = hull_ring(z)
    if not rear:
        center = (0, (ring[0][1] + ring[7][1]) / 2, z)
        return tuple((center, a, b) for a, b in zip(ring, ring[1:] + ring[:1]))

    # The liftgate is inset beneath its lip, then blends into a round bumper.
    columns = (-1, -0.80 / 0.948, -0.53 / 0.948, -0.27, 0, 0.27, 0.53 / 0.948, 0.80 / 0.948, 1)
    top = [(ring[3][0] * t, *interpolate(ring[:4], ring[3][0] * abs(t))) for t in columns]
    side = sorted((y, x, depth) for x, y, depth in ring[3:7])
    contours = ((0.698, 0.657, 2.261), (0.624, 0.600, 2.262),
                (0.553, ring[4][1], 2.230), (0.475, 0.436, 2.244),
                (0.410, 0.368, 2.270), (0.339, 0.298, 2.249),
                (0.272, ring[5][1], 2.228))
    rows = [top]
    for middle_y, edge_y, middle_z in contours:
        edge_x, edge_z = interpolate(side, edge_y)
        rows.append([(edge_x * t, middle_y + (edge_y - middle_y) * t * t,
                      middle_z + (edge_z - middle_z) * abs(t) ** 2.7) for t in columns])
    rows.append([(ring[6][0] * t, ring[6][1], z + (ring[6][2] - z) * abs(t)) for t in columns])
    triangles = []
    for upper, lower in zip(rows, rows[1:]):
        for i in range(len(columns) - 1):
            triangles.extend(((upper[i], upper[i + 1], lower[i + 1]),
                              (upper[i], lower[i + 1], lower[i])))
    return tuple(triangles)


def fascia_depth(x, y, rear):
    # Place trim on the same triangles as the bumper, including the curved corners.
    for a, b, c in fascia_section(rear):
        bx, by, bz = subtract(b, a)
        cx, cy, cz = subtract(c, a)
        dx, dy = x - a[0], y - a[1]
        determinant = bx * cy - by * cx
        if abs(determinant) < 1e-9:
            continue
        u = (dx * cy - dy * cx) / determinant
        v = (bx * dy - by * dx) / determinant
        if u >= -1e-6 and v >= -1e-6 and u + v <= 1.000001:
            return a[2] + u * bz + v * cz
    return (2.25 - 0.103 * abs(x)) if rear else (-2.22 + 0.104 * abs(x))


def fascia_point(x, y, offset=0, rear=True):
    return (x, y, fascia_depth(x, y, rear) + (offset if rear else -offset))


def ribbon(mesh, points, width, offset=0, rear=True):
    outward = (0, 0, 1 if rear else -1)
    normals = []
    for (x1, y1), (x2, y2) in zip(points, points[1:]):
        magnitude = math.hypot(x2 - x1, y2 - y1)
        normals.append(((y1 - y2) / magnitude, (x2 - x1) / magnitude))
    edges = []
    for i, (x, y) in enumerate(points):
        before = normals[max(0, i - 1)]
        after = normals[min(i, len(normals) - 1)]
        scale = width / (2 * (1 + dot(before, after)))
        nx, ny = ((a + b) * scale for a, b in zip(before, after))
        edges.append((fascia_point(x + nx, y + ny, offset, rear),
                      fascia_point(x - nx, y - ny, offset, rear)))
    for (a, b), (c, d) in zip(edges, edges[1:]):
        mesh.face([a, c, d, b], outward)


def make_trim(meshes):
    lens = Mesh("Continuous smoked rear lens", "tail_lens")
    light = Mesh("Red rear light blade", "tail_glow")
    hatch = Mesh("Liftgate shut lines", "seam")
    diffuser = Mesh("Lower diffuser and four fins", "diffuser")
    lip = Mesh("Bumper and diffuser lower edge", "bright_trim")
    reflectors = Mesh("Lower red reflectors", "rear_reflector")
    headlamps = Mesh("Front LED headlights", "headlamp")
    grille = Mesh("Lower air inlet", "front_grille")
    meshes.extend((lens, light, hatch, diffuser, lip, reflectors, headlamps, grille))
    half = [(0, 0.616), (0.22, 0.616), (0.43, 0.618), (0.60, 0.622),
            (0.70, 0.631), (0.79, 0.648), (0.831, 0.668), (0.849, 0.687)]
    strip = [(-x, y) for x, y in reversed(half[1:])] + half
    ribbon(lens, strip, 0.044, 0.018)
    ribbon(light, strip, 0.012, 0.024)

    left = [(-0.687, 0.751), (-0.661, 0.697), (-0.654, 0.607), (-0.637, 0.490),
            (-0.610, 0.446), (-0.550, 0.430), (0, 0.423)]
    ribbon(hatch, left + [(-x, y) for x, y in reversed(left[:-1])], 0.007, 0.026)
    diffuser_top = [(0, 0.330), (0.50, 0.331), (0.661, 0.389),
                    (0.789, 0.381), (0.834, 0.326)]
    diffuser_bottom = [(0, 0.219), (0.50, 0.216), (0.661, 0.204),
                       (0.789, 0.216), (0.834, 0.268)]
    top = [(-x, y) for x, y in reversed(diffuser_top[1:])] + diffuser_top
    bottom = [(-x, y) for x, y in reversed(diffuser_bottom[1:])] + diffuser_bottom
    for i in range(len(top) - 1):
        diffuser.face([fascia_point(*top[i], 0.022), fascia_point(*top[i + 1], 0.022),
                       fascia_point(*bottom[i + 1], 0.032), fascia_point(*bottom[i], 0.032)], (0, 0, 1))
    ribbon(lip, bottom, 0.016, 0.038)
    for x in (-0.465, -0.18, 0.18, 0.465):
        fin = [fascia_point(x - 0.019, 0.216, 0.046), fascia_point(x + 0.020, 0.216, 0.046),
               fascia_point(x + 0.008, 0.314, 0.035), fascia_point(x - 0.012, 0.314, 0.035)]
        diffuser.face(fin, (0, 0, 1))
        diffuser.face([fin[1], fascia_point(x + 0.022, 0.203, 0.005),
                       fascia_point(x + 0.008, 0.315, 0.001), fin[2]], (1, 0, 0))

    for sign in (-1, 1):
        ribbon(reflectors, [(sign * 0.568, 0.360), (sign * 0.695, 0.371), (sign * 0.783, 0.363)],
               0.017, 0.038)
        ribbon(headlamps, [(sign * 0.46, 0.576), (sign * 0.67, 0.575), (sign * 0.806, 0.619)],
               0.026, 0.018, rear=False)
    grille.face([fascia_point(-0.58, 0.306, 0.017, False), fascia_point(0.58, 0.306, 0.017, False),
                 fascia_point(0.50, 0.433, 0.012, False), fascia_point(-0.50, 0.433, 0.012, False)],
                (0, 0, -1))


def make_wheel(meshes, sign, z, name):
    tire = Mesh(name + " tire", "tire")
    rim = Mesh(name + " satin rim", "wheel")
    hub = Mesh(name + " wheel center", "wheel_hub")
    meshes.extend((tire, rim, hub))
    x = 0.872 if z < 0 else 0.900
    count = 12

    def point(outset, radius, angle):
        return (sign * (x + outset), WHEEL_RADIUS + radius * math.cos(angle),
                z + radius * math.sin(angle))

    sections = ((-0.126, 0.262), (-0.079, WHEEL_RADIUS),
                (0.069, WHEEL_RADIUS), (0.123, 0.253))
    for i in range(count):
        a = 2 * math.pi * i / count
        b = 2 * math.pi * (i + 1) / count
        outward = (0, math.cos((a + b) / 2), math.sin((a + b) / 2))
        for (p1, r1), (p2, r2) in zip(sections, sections[1:]):
            tire.face([point(p1, r1, a), point(p1, r1, b),
                       point(p2, r2, b), point(p2, r2, a)], outward)
        tire.face([point(0.123, 0.253, a), point(0.123, 0.253, b),
                   point(0.125, 0.219, b), point(0.125, 0.219, a)], (sign, 0, 0))
        rim.face([point(0.125, 0.219, a), point(0.125, 0.219, b),
                  point(0.126, 0.196, b), point(0.126, 0.196, a)], (sign, 0, 0))
        hub.face([point(0.110, 0, a), point(0.110, 0.196, a),
                  point(0.110, 0.196, b)], (sign, 0, 0))
    for i in range(5):
        angle = 2 * math.pi * i / 5
        rim.face([point(0.127, 0.048, angle - 0.24), point(0.127, 0.196, angle + 0.05),
                  point(0.127, 0.196, angle + 0.19), point(0.127, 0.048, angle + 0.37)],
                 (sign, 0, 0))


def make_mirror(meshes, sign):
    housing = Mesh("Left mirror housing" if sign < 0 else "Right mirror housing", "body")
    glass = Mesh("Left mirror glass" if sign < 0 else "Right mirror glass", "mirror")
    meshes.extend((housing, glass))
    outline = [(0.752, 0.958), (0.888, 1.017), (1.019, 1.029),
               (1.114, 0.997), (1.108, 0.953), (0.997, 0.932), (0.790, 0.936)]
    center = (0.975, 0.977)

    def ring(z, scale):
        return [(sign * (center[0] + (x - center[0]) * scale),
                 center[1] + (y - center[1]) * scale, z) for x, y in outline]

    fore, aft = ring(-0.993, 0.81), ring(-0.848, 1)
    for i in range(len(outline)):
        j = (i + 1) % len(outline)
        radial = (sign * ((outline[i][0] + outline[j][0]) * 0.5 - center[0]),
                  (outline[i][1] + outline[j][1]) * 0.5 - center[1], 0)
        housing.face([fore[i], aft[i], aft[j], fore[j]], radial)
    housing.face(fore, (0, 0, -1))
    housing.face(aft, (0, 0, 1))
    glass.face(ring(-0.842, 0.83), (0, 0, 1))


def build():
    meshes = []
    make_body(meshes)
    make_cabin(meshes)
    make_trim(meshes)
    for axle, z in zip(("Front", "Rear"), AXLES):
        for side, sign in (("left", -1), ("right", 1)):
            make_wheel(meshes, sign, z, axle + " " + side)
    for sign in (-1, 1):
        make_mirror(meshes, sign)
    return [mesh for mesh in meshes if mesh.triangles]


def export_obj(meshes, folder):
    folder.mkdir(parents=True, exist_ok=True)
    with (folder / (MODEL + ".obj")).open("w", encoding="utf-8", newline="\n") as out:
        out.write("# CupFight fastback. x right, y up, z rear.\n")
        out.write("mtllib " + MODEL + ".mtl\n")
        first = 1
        for mesh in meshes:
            out.write("\no " + mesh.name.replace(" ", "_") + "\nusemtl " + mesh.material + "\n")
            for p in mesh.vertices:
                out.write("v {:.6f} {:.6f} {:.6f}\n".format(*p))
            for n in mesh.normals():
                out.write("vn {:.6f} {:.6f} {:.6f}\n".format(*n))
            for tri in mesh.triangles:
                out.write("f " + " ".join(f"{index + first}//{index + first}" for index in tri) + "\n")
            first += len(mesh.vertices)
    with (folder / (MODEL + ".mtl")).open("w", encoding="utf-8", newline="\n") as out:
        for name, material in MATERIALS.items():
            spec = 0.08 + material.metallic * 0.63
            shine = 10 + (1 - material.roughness) ** 2 * 180
            out.write(f"newmtl {name}\n")
            out.write("Kd {:.3f} {:.3f} {:.3f}\n".format(*material.rgb))
            out.write("Ks {:.3f} {:.3f} {:.3f}\n".format(spec, spec, spec))
            out.write("Ke {:.3f} {:.3f} {:.3f}\n".format(*material.emission))
            out.write(f"Ns {shine:.1f}\nillum 2\n\n")


def linear(value):
    return value / 12.92 if value <= 0.04045 else ((value + 0.055) / 1.055) ** 2.4


def export_glb(meshes, path):
    data = bytearray()
    materials = [
        {
            "name": name,
            "pbrMetallicRoughness": {
                "baseColorFactor": [*(linear(channel) for channel in item.rgb), 1],
                "metallicFactor": item.metallic,
                "roughnessFactor": item.roughness,
            },
            "emissiveFactor": item.emission,
        }
        for name, item in MATERIALS.items()
    ]
    document = {"asset": {"version": "2.0", "generator": "CupFight parametric vehicle"},
                "scene": 0, "scenes": [{"nodes": list(range(len(meshes)))}],
                "nodes": [], "meshes": [], "materials": materials,
                "bufferViews": [], "accessors": []}

    def accessor(values, fmt, component_type, value_type, target, bounds=None):
        data.extend(b"\0" * (-len(data) % 4))
        offset = len(data)
        for value in values:
            data.extend(struct.pack(fmt, *value))
        view = len(document["bufferViews"])
        document["bufferViews"].append({"buffer": 0, "byteOffset": offset,
                                        "byteLength": len(data) - offset, "target": target})
        item = {"bufferView": view, "componentType": component_type,
                "count": len(values), "type": value_type}
        if bounds is not None:
            item["min"], item["max"] = bounds
        document["accessors"].append(item)
        return len(document["accessors"]) - 1

    material_indices = {name: index for index, name in enumerate(MATERIALS)}
    for index, mesh in enumerate(meshes):
        coordinates = list(zip(*mesh.vertices))
        limits = ([min(axis) for axis in coordinates], [max(axis) for axis in coordinates])
        positions = accessor(mesh.vertices, "<3f", 5126, "VEC3", 34962, limits)
        normals = accessor(mesh.normals(), "<3f", 5126, "VEC3", 34962)
        indices = accessor([(i,) for face in mesh.triangles for i in face], "<H", 5123, "SCALAR", 34963)
        primitive = {"attributes": {"POSITION": positions, "NORMAL": normals},
                     "indices": indices, "material": material_indices[mesh.material]}
        document["meshes"].append({"name": mesh.name, "primitives": [primitive]})
        document["nodes"].append({"name": mesh.name, "mesh": index})

    document["buffers"] = [{"byteLength": len(data)}]
    packed = json.dumps(document, separators=(",", ":")).encode("utf-8")
    packed += b" " * (-len(packed) % 4)
    data.extend(b"\0" * (-len(data) % 4))
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("wb") as out:
        out.write(struct.pack("<4sII", b"glTF", 2, 28 + len(packed) + len(data)))
        out.write(struct.pack("<I4s", len(packed), b"JSON"))
        out.write(packed)
        out.write(struct.pack("<I4s", len(data), b"BIN\0"))
        out.write(data)


if __name__ == "__main__":
    car = build()
    export_obj(car, ROOT / "app/src/main/assets")
    export_glb(car, ROOT / "artwork" / (MODEL + ".glb"))
    print(f"{len(car)} parts, {sum(len(m.vertices) for m in car)} vertices, "
          f"{sum(len(m.triangles) for m in car)} triangles")

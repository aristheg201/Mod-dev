# Fabric MMO ports overlay M2.2

MythicMobs particle-geometry parity slice built on top of the M2.1 Fabric execution runtime.

Implemented as normal Java source rather than another base64 source archive.

Implemented geometry:

- `particleline`
- `particlelinehelix`
- `particlelinering`
- `particlering`
- `particleorbital`
- `particlesphere`
- `particlebox`
- `particlewave`
- `particletornado`
- `particleatom`

The generic `particle`/`particles` mechanics still use the normal point emitter. `particleequation` and `particlelineequation` remain compatibility fallbacks in this batch and are not claimed as full equation parity yet.

Geometry output is bounded to prevent a malformed legacy config from emitting an unbounded number of points in one cast.

Local Java 21 engine compile and smoke gate:

```text
MYTHICMOBS_PARTICLE_GEOMETRY_M2_2=PASS particles=92
```

The smoke skill combines ring, sphere, box, tornado and atom geometry and verifies the exact emitted point count. `mythicmobs-fabric/build.gradle` overlays this source onto the verified M2.1 source before Loom compilation so mapping/API errors cannot hide behind post-build class merging.

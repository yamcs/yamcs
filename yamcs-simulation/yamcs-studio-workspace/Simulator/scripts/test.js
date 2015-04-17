require(['Cesium'], function(Cesium) {
    "use strict";

    var viewer = new Cesium.Viewer('cesiumContainer');
    var scene = viewer.scene;
    var primitives = scene.getPrimitives();
    var ellipsoid = viewer.centralBody.getEllipsoid();

/// Fixed radius definition for all the waypoints

     var radius = 30000.0;

    // Create sphere are position with model matrix

    var positionOnEllipsoid = ellipsoid.cartographicToCartesian(Cesium.Cartographic.fromDegrees(-60.55, 53.3, 40000));
    var modelMatrix = Cesium.Matrix4.multiplyByTranslation(
        Cesium.Transforms.eastNorthUpToFixedFrame(positionOnEllipsoid),
        new Cesium.Cartesian3(0.0, 0.0, radius)
    );

    var sphereGeometry = new Cesium.SphereGeometry({
        vertexFormat : Cesium.PerInstanceColorAppearance.VERTEX_FORMAT,
        radius : radius
    });

    var waypoint1 = new Cesium.GeometryInstance({
        geometry : sphereGeometry,
        modelMatrix : modelMatrix,
        attributes : {
            color : Cesium.ColorGeometryInstanceAttribute.fromColor(Cesium.Color.WHITE)
        }
    });

    // Create sphere are position with model matrix

    var positionOnEllipsoid = ellipsoid.cartographicToCartesian(Cesium.Cartographic.fromDegrees(-60.55, 53.3, 38066));
    var modelMatrix = Cesium.Matrix4.multiplyByTranslation(
        Cesium.Transforms.eastNorthUpToFixedFrame(positionOnEllipsoid),
        new Cesium.Cartesian3(0.0, 0.0, radius)
    );

    var sphereGeometry = new Cesium.SphereGeometry({
        vertexFormat : Cesium.PerInstanceColorAppearance.VERTEX_FORMAT,
        radius : radius
    });

    var waypoint2 = new Cesium.GeometryInstance({
        geometry : sphereGeometry,
        modelMatrix : modelMatrix,
        attributes : {
            color : Cesium.ColorGeometryInstanceAttribute.fromColor(Cesium.Color.WHITE)
        }
    });


        // Create sphere are position with model matrix

    var positionOnEllipsoid = ellipsoid.cartographicToCartesian(Cesium.Cartographic.fromDegrees(-60.549, 53.297, 32368));
    var modelMatrix = Cesium.Matrix4.multiplyByTranslation(
        Cesium.Transforms.eastNorthUpToFixedFrame(positionOnEllipsoid),
        new Cesium.Cartesian3(0.0, 0.0, radius)
    );

    var sphereGeometry = new Cesium.SphereGeometry({
        vertexFormat : Cesium.PerInstanceColorAppearance.VERTEX_FORMAT,
        radius : radius
    });

    var waypoint3 = new Cesium.GeometryInstance({
        geometry : sphereGeometry,
        modelMatrix : modelMatrix,
        attributes : {
            color : Cesium.ColorGeometryInstanceAttribute.fromColor(Cesium.Color.WHITE)
        }
    });



            // Create sphere are position with model matrix

    var positionOnEllipsoid = ellipsoid.cartographicToCartesian(Cesium.Cartographic.fromDegrees(-60.543, 53.287, 24075));
    var modelMatrix = Cesium.Matrix4.multiplyByTranslation(
        Cesium.Transforms.eastNorthUpToFixedFrame(positionOnEllipsoid),
        new Cesium.Cartesian3(0.0, 0.0, radius)
    );

    var sphereGeometry = new Cesium.SphereGeometry({
        vertexFormat : Cesium.PerInstanceColorAppearance.VERTEX_FORMAT,
        radius : radius
    });

    var waypoint4 = new Cesium.GeometryInstance({
        geometry : sphereGeometry,
        modelMatrix : modelMatrix,
        attributes : {
            color : Cesium.ColorGeometryInstanceAttribute.fromColor(Cesium.Color.WHITE)
        }
    });




             // Create sphere are position with model matrix

    var positionOnEllipsoid = ellipsoid.cartographicToCartesian(Cesium.Cartographic.fromDegrees(-60.519, 53.259, 18069));
    var modelMatrix = Cesium.Matrix4.multiplyByTranslation(
        Cesium.Transforms.eastNorthUpToFixedFrame(positionOnEllipsoid),
        new Cesium.Cartesian3(0.0, 0.0, radius)
    );

    var sphereGeometry = new Cesium.SphereGeometry({
        vertexFormat : Cesium.PerInstanceColorAppearance.VERTEX_FORMAT,
        radius : radius
    });

    var waypoint5 = new Cesium.GeometryInstance({
        geometry : sphereGeometry,
        modelMatrix : modelMatrix,
        attributes : {
            color : Cesium.ColorGeometryInstanceAttribute.fromColor(Cesium.Color.WHITE)
        }
    });



                 // Create sphere are position with model matrix

    var positionOnEllipsoid = ellipsoid.cartographicToCartesian(Cesium.Cartographic.fromDegrees(-60.498, 53.19, 11760));
    var modelMatrix = Cesium.Matrix4.multiplyByTranslation(
        Cesium.Transforms.eastNorthUpToFixedFrame(positionOnEllipsoid),
        new Cesium.Cartesian3(0.0, 0.0, radius)
    );

    var sphereGeometry = new Cesium.SphereGeometry({
        vertexFormat : Cesium.PerInstanceColorAppearance.VERTEX_FORMAT,
        radius : radius
    });

    var waypoint6 = new Cesium.GeometryInstance({
        geometry : sphereGeometry,
        modelMatrix : modelMatrix,
        attributes : {
            color : Cesium.ColorGeometryInstanceAttribute.fromColor(Cesium.Color.WHITE)
        }
    });




                    // Create sphere are position with model matrix

    var positionOnEllipsoid = ellipsoid.cartographicToCartesian(Cesium.Cartographic.fromDegrees(-60.532, 53.167, 9229.1));
    var modelMatrix = Cesium.Matrix4.multiplyByTranslation(
        Cesium.Transforms.eastNorthUpToFixedFrame(positionOnEllipsoid),
        new Cesium.Cartesian3(0.0, 0.0, radius)
    );

    var sphereGeometry = new Cesium.SphereGeometry({
        vertexFormat : Cesium.PerInstanceColorAppearance.VERTEX_FORMAT,
        radius : radius
    });

    var waypoint7 = new Cesium.GeometryInstance({
        geometry : sphereGeometry,
        modelMatrix : modelMatrix,
        attributes : {
            color : Cesium.ColorGeometryInstanceAttribute.fromColor(Cesium.Color.WHITE)
        }
    });




                    // Create sphere are position with model matrix

    var positionOnEllipsoid = ellipsoid.cartographicToCartesian(Cesium.Cartographic.fromDegrees(-60.581, 53.165, 7813.3));
    var modelMatrix = Cesium.Matrix4.multiplyByTranslation(
        Cesium.Transforms.eastNorthUpToFixedFrame(positionOnEllipsoid),
        new Cesium.Cartesian3(0.0, 0.0, radius)
    );

    var sphereGeometry = new Cesium.SphereGeometry({
        vertexFormat : Cesium.PerInstanceColorAppearance.VERTEX_FORMAT,
        radius : radius
    });

    var waypoint8 = new Cesium.GeometryInstance({
        geometry : sphereGeometry,
        modelMatrix : modelMatrix,
        attributes : {
            color : Cesium.ColorGeometryInstanceAttribute.fromColor(Cesium.Color.WHITE)
        }
    });




                    // Create sphere are position with model matrix

    var positionOnEllipsoid = ellipsoid.cartographicToCartesian(Cesium.Cartographic.fromDegrees(-60.615, 53.178, 6591));
    var modelMatrix = Cesium.Matrix4.multiplyByTranslation(
        Cesium.Transforms.eastNorthUpToFixedFrame(positionOnEllipsoid),
        new Cesium.Cartesian3(0.0, 0.0, radius)
    );

    var sphereGeometry = new Cesium.SphereGeometry({
        vertexFormat : Cesium.PerInstanceColorAppearance.VERTEX_FORMAT,
        radius : radius
    });

    var waypoint9 = new Cesium.GeometryInstance({
        geometry : sphereGeometry,
        modelMatrix : modelMatrix,
        attributes : {
            color : Cesium.ColorGeometryInstanceAttribute.fromColor(Cesium.Color.WHITE)
        }
    });




                    // Create sphere are position with model matrix

    var positionOnEllipsoid = ellipsoid.cartographicToCartesian(Cesium.Cartographic.fromDegrees(-60.615, 53.2, 5562));
    var modelMatrix = Cesium.Matrix4.multiplyByTranslation(
        Cesium.Transforms.eastNorthUpToFixedFrame(positionOnEllipsoid),
        new Cesium.Cartesian3(0.0, 0.0, radius)
    );

    var sphereGeometry = new Cesium.SphereGeometry({
        vertexFormat : Cesium.PerInstanceColorAppearance.VERTEX_FORMAT,
        radius : radius
    });

    var waypoint10 = new Cesium.GeometryInstance({
        geometry : sphereGeometry,
        modelMatrix : modelMatrix,
        attributes : {
            color : Cesium.ColorGeometryInstanceAttribute.fromColor(Cesium.Color.WHITE)
        }
    });



                    // Create sphere are position with model matrix

    var positionOnEllipsoid = ellipsoid.cartographicToCartesian(Cesium.Cartographic.fromDegrees(-60.6, 53.218, 4591.6));
    var modelMatrix = Cesium.Matrix4.multiplyByTranslation(
        Cesium.Transforms.eastNorthUpToFixedFrame(positionOnEllipsoid),
        new Cesium.Cartesian3(0.0, 0.0, radius)
    );

    var sphereGeometry = new Cesium.SphereGeometry({
        vertexFormat : Cesium.PerInstanceColorAppearance.VERTEX_FORMAT,
        radius : radius
    });

    var waypoint11 = new Cesium.GeometryInstance({
        geometry : sphereGeometry,
        modelMatrix : modelMatrix,
        attributes : {
            color : Cesium.ColorGeometryInstanceAttribute.fromColor(Cesium.Color.WHITE)
        }
    });



                    // Create sphere are position with model matrix

    var positionOnEllipsoid = ellipsoid.cartographicToCartesian(Cesium.Cartographic.fromDegrees(-60.585, 53.235, 3702.2));
    var modelMatrix = Cesium.Matrix4.multiplyByTranslation(
        Cesium.Transforms.eastNorthUpToFixedFrame(positionOnEllipsoid),
        new Cesium.Cartesian3(0.0, 0.0, radius)
    );

    var sphereGeometry = new Cesium.SphereGeometry({
        vertexFormat : Cesium.PerInstanceColorAppearance.VERTEX_FORMAT,
        radius : radius
    });

    var waypoint12 = new Cesium.GeometryInstance({
        geometry : sphereGeometry,
        modelMatrix : modelMatrix,
        attributes : {
            color : Cesium.ColorGeometryInstanceAttribute.fromColor(Cesium.Color.WHITE)
        }
    });



                    // Create sphere are position with model matrix

    var positionOnEllipsoid = ellipsoid.cartographicToCartesian(Cesium.Cartographic.fromDegrees(-60.568, 53.249, 2807.82));
    var modelMatrix = Cesium.Matrix4.multiplyByTranslation(
        Cesium.Transforms.eastNorthUpToFixedFrame(positionOnEllipsoid),
        new Cesium.Cartesian3(0.0, 0.0, radius)
    );

    var sphereGeometry = new Cesium.SphereGeometry({
        vertexFormat : Cesium.PerInstanceColorAppearance.VERTEX_FORMAT,
        radius : radius
    });

    var waypoint13 = new Cesium.GeometryInstance({
        geometry : sphereGeometry,
        modelMatrix : modelMatrix,
        attributes : {
            color : Cesium.ColorGeometryInstanceAttribute.fromColor(Cesium.Color.WHITE)
        }
    });



                        // Create sphere are position with model matrix

    var positionOnEllipsoid = ellipsoid.cartographicToCartesian(Cesium.Cartographic.fromDegrees(-60.549, 53.262, 1992.4));
    var modelMatrix = Cesium.Matrix4.multiplyByTranslation(
        Cesium.Transforms.eastNorthUpToFixedFrame(positionOnEllipsoid),
        new Cesium.Cartesian3(0.0, 0.0, radius)
    );

    var sphereGeometry = new Cesium.SphereGeometry({
        vertexFormat : Cesium.PerInstanceColorAppearance.VERTEX_FORMAT,
        radius : radius
    });

    var waypoint14 = new Cesium.GeometryInstance({
        geometry : sphereGeometry,
        modelMatrix : modelMatrix,
        attributes : {
            color : Cesium.ColorGeometryInstanceAttribute.fromColor(Cesium.Color.WHITE)
        }
    });


                            // Create sphere are position with model matrix

    var positionOnEllipsoid = ellipsoid.cartographicToCartesian(Cesium.Cartographic.fromDegrees(-60.527, 53.271, 1341.6));
    var modelMatrix = Cesium.Matrix4.multiplyByTranslation(
        Cesium.Transforms.eastNorthUpToFixedFrame(positionOnEllipsoid),
        new Cesium.Cartesian3(0.0, 0.0, radius)
    );

    var sphereGeometry = new Cesium.SphereGeometry({
        vertexFormat : Cesium.PerInstanceColorAppearance.VERTEX_FORMAT,
        radius : radius
    });

    var waypoint15 = new Cesium.GeometryInstance({
        geometry : sphereGeometry,
        modelMatrix : modelMatrix,
        attributes : {
            color : Cesium.ColorGeometryInstanceAttribute.fromColor(Cesium.Color.WHITE)
        }
    });


                                // Create sphere are position with model matrix

    var positionOnEllipsoid = ellipsoid.cartographicToCartesian(Cesium.Cartographic.fromDegrees(-60.507, 53.279, 889.9));
    var modelMatrix = Cesium.Matrix4.multiplyByTranslation(
        Cesium.Transforms.eastNorthUpToFixedFrame(positionOnEllipsoid),
        new Cesium.Cartesian3(0.0, 0.0, radius)
    );

    var sphereGeometry = new Cesium.SphereGeometry({
        vertexFormat : Cesium.PerInstanceColorAppearance.VERTEX_FORMAT,
        radius : radius
    });

    var waypoint16 = new Cesium.GeometryInstance({
        geometry : sphereGeometry,
        modelMatrix : modelMatrix,
        attributes : {
            color : Cesium.ColorGeometryInstanceAttribute.fromColor(Cesium.Color.WHITE)
        }
    });




                                // Create sphere are position with model matrix

    var positionOnEllipsoid = ellipsoid.cartographicToCartesian(Cesium.Cartographic.fromDegrees(-60.49, 53.286, 596.69));
    var modelMatrix = Cesium.Matrix4.multiplyByTranslation(
        Cesium.Transforms.eastNorthUpToFixedFrame(positionOnEllipsoid),
        new Cesium.Cartesian3(0.0, 0.0, radius)
    );

    var sphereGeometry = new Cesium.SphereGeometry({
        vertexFormat : Cesium.PerInstanceColorAppearance.VERTEX_FORMAT,
        radius : radius
    });

    var waypoint17 = new Cesium.GeometryInstance({
        geometry : sphereGeometry,
        modelMatrix : modelMatrix,
        attributes : {
            color : Cesium.ColorGeometryInstanceAttribute.fromColor(Cesium.Color.WHITE)
        }
    });


                                    // Create sphere are position with model matrix

    var positionOnEllipsoid = ellipsoid.cartographicToCartesian(Cesium.Cartographic.fromDegrees(-60.475, 53.293, 364.08));
    var modelMatrix = Cesium.Matrix4.multiplyByTranslation(
        Cesium.Transforms.eastNorthUpToFixedFrame(positionOnEllipsoid),
        new Cesium.Cartesian3(0.0, 0.0, radius)
    );

    var sphereGeometry = new Cesium.SphereGeometry({
        vertexFormat : Cesium.PerInstanceColorAppearance.VERTEX_FORMAT,
        radius : radius
    });

    var waypoint18 = new Cesium.GeometryInstance({
        geometry : sphereGeometry,
        modelMatrix : modelMatrix,
        attributes : {
            color : Cesium.ColorGeometryInstanceAttribute.fromColor(Cesium.Color.WHITE)
        }
    });




                                    // Create sphere are position with model matrix

    var positionOnEllipsoid = ellipsoid.cartographicToCartesian(Cesium.Cartographic.fromDegrees(-60.462, 53.298, 183.98));
    var modelMatrix = Cesium.Matrix4.multiplyByTranslation(
        Cesium.Transforms.eastNorthUpToFixedFrame(positionOnEllipsoid),
        new Cesium.Cartesian3(0.0, 0.0, radius)
    );

    var sphereGeometry = new Cesium.SphereGeometry({
        vertexFormat : Cesium.PerInstanceColorAppearance.VERTEX_FORMAT,
        radius : radius
    });

    var waypoint19 = new Cesium.GeometryInstance({
        geometry : sphereGeometry,
        modelMatrix : modelMatrix,
        attributes : {
            color : Cesium.ColorGeometryInstanceAttribute.fromColor(Cesium.Color.WHITE)
        }
    });

                                    // Create sphere are position with model matrix

    var positionOnEllipsoid = ellipsoid.cartographicToCartesian(Cesium.Cartographic.fromDegrees(-60.451, 53.303, 51.5));
    var modelMatrix = Cesium.Matrix4.multiplyByTranslation(
        Cesium.Transforms.eastNorthUpToFixedFrame(positionOnEllipsoid),
        new Cesium.Cartesian3(0.0, 0.0, radius)
    );

    var sphereGeometry = new Cesium.SphereGeometry({
        vertexFormat : Cesium.PerInstanceColorAppearance.VERTEX_FORMAT,
        radius : radius
    });

    var waypoint20 = new Cesium.GeometryInstance({
        geometry : sphereGeometry,
        modelMatrix : modelMatrix,
        attributes : {
            color : Cesium.ColorGeometryInstanceAttribute.fromColor(Cesium.Color.WHITE)
        }
    });




    // Add instances to primitives
    primitives.add(new Cesium.Primitive({
        geometryInstances : [waypoint1, waypoint2, waypoint3, waypoint4, waypoint5, waypoint6, waypoint7, waypoint8, waypoint9, waypoint10, waypoint11, waypoint12, waypoint13, waypoint14, waypoint15, waypoint16, waypoint17, waypoint18, waypoint19, waypoint20],
        appearance : new Cesium.PerInstanceColorAppearance({
            closed : true,
            translucent: false
        })
    }));

    Sandcastle.finishedLoading();
});

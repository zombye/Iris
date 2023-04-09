package net.irisshaders.iris;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3f;

public class JomlConversions {
	public static Vector3d fromVec3(Vec3 vec) {
		return new Vector3d(vec.x(), vec.y(), vec.z());
	}

	public static Vector3f fromVec3Float(Vec3 vec) {
		return new Vector3f((float) vec.x(), (float) vec.y(), (float) vec.z());
	}
}

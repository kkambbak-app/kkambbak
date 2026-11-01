package app.kkambbak.tools

import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Paparazzi 기본 기록은 이미지를 축소한다. 영상·화면 시안용으로 원본 해상도 그대로 저장한다. */
class FullResHandler(private val dir: File) : SnapshotHandler {
    init { dir.mkdirs() }
    override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int) = object : SnapshotHandler.FrameHandler {
        override fun handle(image: BufferedImage) { ImageIO.write(image, "PNG", File(dir, "${snapshot.name ?: snapshot.testName.methodName}.png")) }
        override fun close() {}
    }
    override fun close() {}
}

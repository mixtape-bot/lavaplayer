package com.sedmelluq.discord.lavaplayer.filter.equalizer

internal object EqualizerConstants {
    const val BAND_COUNT = 15
    const val SAMPLE_RATE = 48000

    @JvmField
    val COEFFICIENTS = arrayOf(
        Coefficients(9.9847546664e-01f, 7.6226668143e-04f, 1.9984647656e+00f),
        Coefficients(9.9756184654e-01f, 1.2190767289e-03f, 1.9975344645e+00f),
        Coefficients(9.9616261379e-01f, 1.9186931041e-03f, 1.9960947369e+00f),
        Coefficients(9.9391578543e-01f, 3.0421072865e-03f, 1.9937449618e+00f),
        Coefficients(9.9028307215e-01f, 4.8584639242e-03f, 1.9898465702e+00f),
        Coefficients(9.8485897264e-01f, 7.5705136795e-03f, 1.9837962543e+00f),
        Coefficients(9.7588512657e-01f, 1.2057436715e-02f, 1.9731772447e+00f),
        Coefficients(9.6228521814e-01f, 1.8857390928e-02f, 1.9556164694e+00f),
        Coefficients(9.4080933132e-01f, 2.9595334338e-02f, 1.9242054384e+00f),
        Coefficients(9.0702059196e-01f, 4.6489704022e-02f, 1.8653476166e+00f),
        Coefficients(8.5868004289e-01f, 7.0659978553e-02f, 1.7600401337e+00f),
        Coefficients(7.8409610788e-01f, 1.0795194606e-01f, 1.5450725522e+00f),
        Coefficients(6.8332861002e-01f, 1.5833569499e-01f, 1.1426447155e+00f),
        Coefficients(5.5267518228e-01f, 2.2366240886e-01f, 4.0186190803e-01f),
        Coefficients(4.1811888447e-01f, 2.9094055777e-01f, -7.0905944223e-01f)
    )


    data class Coefficients(val beta: Float, val alpha: Float, val gamma: Float)
}

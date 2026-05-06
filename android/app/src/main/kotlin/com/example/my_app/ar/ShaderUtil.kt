package com.example.my_app.ar

import android.opengl.GLES20
import android.util.Log

object ShaderUtil {

    private const val TAG = "ShaderUtil"

    fun buildProgram(vertexSrc: String, fragmentSrc: String): Int {

        val vs = compile(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)

        val program = GLES20.glCreateProgram()

        if (program == 0) {
            throw RuntimeException("Failed to create GL program")
        }

        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)

        GLES20.glLinkProgram(program)

        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)

        if (status[0] == 0) {
            val error = GLES20.glGetProgramInfoLog(program)
            Log.e(TAG, "Program link failed:\n$error")

            GLES20.glDeleteProgram(program)
            throw RuntimeException("Program link failed:\n$error")
        }

        // Clean up shaders after linking
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)

        Log.d(TAG, "Program linked successfully")

        return program
    }

    private fun compile(type: Int, src: String): Int {

        val shader = GLES20.glCreateShader(type)

        if (shader == 0) {
            throw RuntimeException("Error creating shader.")
        }

        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)

        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)

        if (status[0] == 0) {
            val error = GLES20.glGetShaderInfoLog(shader)
            Log.e(TAG, "Shader compile failed:\n$error\nSource:\n$src")

            GLES20.glDeleteShader(shader)
            throw RuntimeException("Shader compile failed:\n$error")
        }

        Log.d(TAG, "Shader compiled successfully")

        return shader
    }
}
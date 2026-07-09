package com.recordweek.utils;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import androidx.core.graphics.ColorUtils;

// Utilidad unica para todo lo relacionado con el color/fondo de una tarjeta de tarea.
// Antes esta logica estaba DUPLICADA en TaskAdapter y MonthlyFragment (mismo mapa de
// colores y mismo constructor de fondo), y ademas AddTaskActivity tenia su propia copia
// del mapa apuntando a R.color.*. Al centralizarla aqui, si algun dia cambia un color o
// el estilo de la tarjeta, se toca UN solo sitio en lugar de tres.
public class CardStyle {

    // Nombre de color guardado en la tarea -> valor ARGB pleno (0xAARRGGBB).
    // Estos hex son identicos a los de res/values/colors.xml (task_red, task_orange...),
    // asi que el resultado visual es el mismo que antes. El caso null/desconocido cae al
    // ambar por defecto (accent_amber #E0922F).
    public static int taskColor(String colorName) {
        if (colorName == null) return 0xFFE0922F;
        switch (colorName) {
            case "RED": return 0xFFEF5350;
            case "ORANGE": return 0xFFFFA726;
            case "YELLOW": return 0xFFFFEE58;
            case "GREEN": return 0xFF66BB6A;
            case "BLUE": return 0xFF5B8DEF;
            case "PURPLE": return 0xFFAB47BC;
            case "PINK": return 0xFFEC407A;
            default: return 0xFFE0922F;
        }
    }

    // Construye el fondo de la tarjeta en tiempo de ejecucion (no se puede en XML porque
    // el color depende de la categoria). Es un LayerDrawable de dos capas:
    //   Capa 0 (tarjeta): rectangulo redondeado con degradado horizontal que va del color
    //     de la categoria muy diluido (8%) hacia la superficie, mas un borde tenue.
    //   Capa 1 (acento): franja de 4dp del color pleno, con las esquinas izquierdas
    //     redondeadas, anclada al borde izquierdo.
    // Se crea una instancia NUEVA en cada llamada: los Drawables de fondo no deben
    // compartirse entre varias vistas visibles (comparten bounds y se pisan).
    public static Drawable cardBackground(Context context, int accentColor) {
        float density = context.getResources().getDisplayMetrics().density;
        float corner = 12 * density;
        int stroke = Math.round(1 * density);
        int accentWidth = Math.round(4 * density);

        int surface = Color.parseColor("#121A2E"); // surface_elevated
        // Mezcla 8% color de categoria + 92% superficie: deja ver el tinte sin restar
        // legibilidad al texto. El degradado de 3 paradas lo confina al tercio izquierdo.
        int tint = ColorUtils.blendARGB(surface, accentColor, 0.08f);

        GradientDrawable card = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{ tint, surface, surface });
        card.setCornerRadius(corner);
        card.setStroke(stroke, Color.parseColor("#232C40")); // borde tenue

        GradientDrawable accent = new GradientDrawable();
        accent.setColor(accentColor);
        // Radios por esquina: [supIzq, supDer, infDer, infIzq]. Solo redondeamos las dos
        // izquierdas para que encaje en la esquina de la tarjeta.
        accent.setCornerRadii(new float[]{ corner, corner, 0, 0, 0, 0, corner, corner });

        LayerDrawable layers = new LayerDrawable(new Drawable[]{ card, accent });
        // setLayerWidth/Gravity: API 23+ (minSdk 29, ok).
        layers.setLayerWidth(1, accentWidth);
        layers.setLayerGravity(1, Gravity.START);
        return layers;
    }
}

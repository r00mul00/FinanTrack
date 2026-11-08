using MudBlazor;

namespace FinanTrack.Web.Theme;

public static class FinanTrackTheme
{
    public static MudTheme Default { get; } = new()
    {
        PaletteDark = new PaletteDark
        {
            Primary = "#7C4DFF",
            Secondary = "#B14DDB",
            Background = "#10111B",
            BackgroundGray = "#25273A",
            Surface = "#1B1D2D",
            DrawerBackground = "#1B1D2D",
            DrawerText = "#A6A8BC",
            DrawerIcon = "#A6A8BC",
            AppbarBackground = "#10111B",
            AppbarText = "#F5F5FA",
            Success = "#20C997",
            Error = "#FF647C",
            Warning = "#FFB547",
            TextPrimary = "#F5F5FA",
            TextSecondary = "#A6A8BC",
            ActionDefault = "#A6A8BC",
            LinesDefault = "#25273A",
            LinesInputs = "#25273A",
            Divider = "#25273A",
            Skeleton = "#25273A"
        },
        // Variante clara provisória; será definida na Fase 7.
        PaletteLight = new PaletteLight
        {
            Primary = "#7C4DFF",
            Success = "#20C997",
            Error = "#FF647C",
            Warning = "#FFB547"
        },
        Typography = new Typography
        {
            Default = new DefaultTypography
            {
                FontFamily = ["Inter", "sans-serif"]
            }
        },
        LayoutProperties = new LayoutProperties
        {
            DefaultBorderRadius = "10px",
            DrawerWidthLeft = "260px"
        }
    };
}

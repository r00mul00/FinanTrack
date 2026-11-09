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
            Tertiary = "#B39BFF",
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
        PaletteLight = new PaletteLight
        {
            Primary = "#6A3DE8",
            Secondary = "#9333C4",
            Tertiary = "#5B2FD6",
            Background = "#F5F6FA",
            BackgroundGray = "#ECEDF4",
            Surface = "#FFFFFF",
            DrawerBackground = "#FFFFFF",
            DrawerText = "#5A5D73",
            DrawerIcon = "#5A5D73",
            AppbarBackground = "#F5F6FA",
            AppbarText = "#1C1E2E",
            Success = "#0A7F5E",
            Error = "#D6304A",
            Warning = "#9A6100",
            TextPrimary = "#1C1E2E",
            TextSecondary = "#5A5D73",
            ActionDefault = "#5A5D73",
            LinesDefault = "#E1E3EC",
            LinesInputs = "#C9CCD9",
            Divider = "#E1E3EC",
            Skeleton = "#E1E3EC"
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
